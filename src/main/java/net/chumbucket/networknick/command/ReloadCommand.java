/*
 * Copyright © 2025 Sorekill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package net.chumbucket.networknick.command;

import net.chumbucket.networknick.redis.RedisBus;
import net.chumbucket.networknick.service.NickService;
import net.chumbucket.networknick.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReloadCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final NickService service;

    // We keep RedisBus via getter/setter so we can replace it on reload
    public interface RedisRef {
        RedisBus get();
        void set(RedisBus bus);
    }

    private final RedisRef redisRef;

    public ReloadCommand(JavaPlugin plugin, NickService service, RedisRef redisRef) {
        this.plugin = plugin;
        this.service = service;
        this.redisRef = redisRef;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("networknick.reload")) {
            Msg.send(sender, "messages.no-perms");
            return true;
        }

        // 1) reload config
        plugin.reloadConfig();

        // 2) restart redis bus so config changes apply (host/channel/prefix/etc)
        RedisBus old = redisRef.get();
        if (old != null) {
            try { old.stop(); } catch (Throwable ignored) {}
        }

        RedisBus fresh = new RedisBus(plugin, service);
        redisRef.set(fresh);
        fresh.start();

        // 3) re-apply names for everyone online (async fetch + sync apply)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String nick = fresh.getNick(p.getUniqueId());
                service.runSync(() -> {
                    Player live = Bukkit.getPlayer(p.getUniqueId());
                    if (live != null && live.isOnline()) {
                        service.applyToPlayer(live, nick);
                    }
                });
            }
        });

        sender.sendMessage(Msg.color("&aNetworkNick reloaded."));
        return true;
    }
}
