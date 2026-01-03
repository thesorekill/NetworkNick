/*
 * Copyright © 2025 Sorekill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package net.chumbucket.networknick.redis;

import net.chumbucket.networknick.service.NickService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RedisBus {

    private final JavaPlugin plugin;
    private final NickService service;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean ssl;
    private final int timeoutMs;

    private final String nickPrefix;
    private final String priorPrefix;
    private final String channel;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread subThread;
    private JedisPubSub pubSub;

    public RedisBus(JavaPlugin plugin, NickService service) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.service = Objects.requireNonNull(service, "service");

        host = plugin.getConfig().getString("redis.host", "127.0.0.1");
        port = plugin.getConfig().getInt("redis.port", 6379);
        username = plugin.getConfig().getString("redis.username", "");
        password = plugin.getConfig().getString("redis.password", "");
        ssl = plugin.getConfig().getBoolean("redis.ssl", false);
        timeoutMs = plugin.getConfig().getInt("redis.timeout-ms", 4000);

        nickPrefix = plugin.getConfig().getString("keys.nick-prefix", "networknick:nick:");
        // NEW: where we store "previous nick before hide"
        priorPrefix = plugin.getConfig().getString("keys.prior-prefix", "networknick:prior:");
        channel = plugin.getConfig().getString("keys.channel", "networknick:updates");
    }

    private Jedis newJedis() {
        DefaultJedisClientConfig.Builder b = DefaultJedisClientConfig.builder()
                .timeoutMillis(timeoutMs)
                .ssl(ssl);

        if (username != null && !username.isBlank()) b.user(username);
        if (password != null && !password.isBlank()) b.password(password);

        return new Jedis(new HostAndPort(host, port), b.build());
    }

    public void start() {
        // fail cleanly if jedis wasn't loaded by Libby
        try {
            Class.forName("redis.clients.jedis.Jedis");
        } catch (Throwable t) {
            plugin.getLogger().severe("Jedis not found on classpath. Did Libby load redis.clients:jedis?");
            plugin.getLogger().severe("RedisBus will NOT start.");
            return;
        }

        if (!running.compareAndSet(false, true)) return;

        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String ch, String message) {
                if (!channel.equals(ch)) return;

                String[] parts = message.split("\\|", 2);
                if (parts.length < 1) return;

                UUID uuid;
                try { uuid = UUID.fromString(parts[0]); }
                catch (Exception ignored) { return; }

                String name = (parts.length == 2) ? parts[1] : "";
                final String finalName = (name == null || name.isBlank()) ? null : name;

                service.runSync(() -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        service.applyToPlayer(p, finalName);
                    }
                });
            }
        };

        subThread = new Thread(() -> {
            while (running.get()) {
                try (Jedis j = newJedis()) {
                    plugin.getLogger().info("Subscribing to Redis channel: " + channel);
                    j.subscribe(pubSub, channel);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Redis subscribe error: " + t.getMessage());
                    try { Thread.sleep(2000L); } catch (InterruptedException ignored) {}
                }
            }
        }, "NetworkNick-RedisSub");
        subThread.setDaemon(true);
        subThread.start();

        // On startup, load & apply for online players
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String nick = getNick(p.getUniqueId());
                service.runSync(() -> service.applyToPlayer(p, nick));
            }
        });
    }

    public void stop() {
        running.set(false);
        if (pubSub != null) {
            try { pubSub.unsubscribe(); } catch (Throwable ignored) {}
        }
    }

    public String getNick(UUID uuid) {
        try (Jedis j = newJedis()) {
            return j.get(nickPrefix + uuid);
        } catch (Throwable t) {
            return null;
        }
    }

    public void setNick(UUID uuid, String nameOrNull) {
        try (Jedis j = newJedis()) {
            String key = nickPrefix + uuid;
            if (nameOrNull == null || nameOrNull.isBlank()) j.del(key);
            else j.set(key, nameOrNull);

            j.publish(channel, uuid + "|" + (nameOrNull == null ? "" : nameOrNull));
        } catch (Throwable t) {
            plugin.getLogger().warning("Redis setNick error: " + t.getMessage());
        }
    }

    // -------------------------
    // NEW: prior nick functions
    // -------------------------

    public String getPriorNick(UUID uuid) {
        try (Jedis j = newJedis()) {
            return j.get(priorPrefix + uuid);
        } catch (Throwable t) {
            return null;
        }
    }

    public void setPriorNick(UUID uuid, String nickOrNull) {
        try (Jedis j = newJedis()) {
            String key = priorPrefix + uuid;
            if (nickOrNull == null || nickOrNull.isBlank()) j.del(key);
            else j.set(key, nickOrNull);
        } catch (Throwable t) {
            plugin.getLogger().warning("Redis setPriorNick error: " + t.getMessage());
        }
    }

    public void clearPriorNick(UUID uuid) {
        setPriorNick(uuid, null);
    }
}
