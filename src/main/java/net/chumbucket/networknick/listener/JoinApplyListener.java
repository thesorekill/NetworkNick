/*
 * Copyright © 2025 Sorekill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package net.chumbucket.networknick.listener;

import net.chumbucket.networknick.redis.RedisBus;
import net.chumbucket.networknick.service.NickService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JoinApplyListener implements Listener {

    private final JavaPlugin plugin;
    private final RedisBus redis;
    private final NickService service;
    private final PreLoginNickCacheListener preloginCache;

    // per-player enforcement task id
    private final Map<UUID, Integer> enforceTasks = new ConcurrentHashMap<>();

    public JoinApplyListener(JavaPlugin plugin, RedisBus redis, NickService service, PreLoginNickCacheListener preloginCache) {
        this.plugin = plugin;
        this.redis = redis;
        this.service = service;
        this.preloginCache = preloginCache;
    }

    /**
     * LOWEST so our displayname is set before join message plugins run.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        final UUID uuid = p.getUniqueId();

        // 1) Apply immediately using pre-login cached nick (no waiting)
        String cached = preloginCache.pop(uuid);
        service.applyToPlayer(p, cached);

        // 2) Async truth-check: fetch from Redis after join (in case cache missed)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String nick = redis.getNick(uuid);

            service.runSync(() -> {
                Player live = Bukkit.getPlayer(uuid);
                if (live == null || !live.isOnline()) return;

                service.applyToPlayer(live, nick);

                // 3) Optional short enforcement to beat late overrides
                stopEnforce(uuid);

                final int maxTicks = 20 * 3; // 3 seconds
                final int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
                    int ticks = 0;

                    @Override
                    public void run() {
                        Player now = Bukkit.getPlayer(uuid);
                        if (now == null || !now.isOnline()) {
                            stopEnforce(uuid);
                            return;
                        }

                        service.applyToPlayer(now, nick);

                        ticks++;
                        if (ticks >= maxTicks) stopEnforce(uuid);
                    }
                }, 1L, 1L);

                enforceTasks.put(uuid, taskId);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        stopEnforce(uuid);
        preloginCache.clear(uuid);
        service.clearFromCache(uuid);
    }

    private void stopEnforce(UUID uuid) {
        Integer id = enforceTasks.remove(uuid);
        if (id != null) {
            try { Bukkit.getScheduler().cancelTask(id); } catch (Throwable ignored) {}
        }
    }
}
