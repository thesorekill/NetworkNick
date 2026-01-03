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
import net.chumbucket.networknick.util.Msg;
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

    private boolean applyDisplayEnabled() {
        return plugin.getConfig().getBoolean("apply.display-name", true);
    }

    private boolean applyListEnabled() {
        return plugin.getConfig().getBoolean("apply.playerlist-name", true);
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
                //    ✅ Now:
                //      - only if we actually apply anything
                //      - runs every 10 ticks (not every tick)
                //      - only reapplies if current value differs
                stopEnforce(uuid);

                final boolean doDisplay = applyDisplayEnabled();
                final boolean doList = applyListEnabled();
                if (!doDisplay && !doList) return;

                final String desiredLegacy = (nick == null || nick.isBlank()) ? live.getName() : nick;
                final String desiredColored = Msg.color(desiredLegacy);

                final int maxRuns = 20 * 3 / 10; // 3 seconds, every 10 ticks => ~6 runs
                final int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
                    int runs = 0;

                    @Override
                    public void run() {
                        Player now = Bukkit.getPlayer(uuid);
                        if (now == null || !now.isOnline()) {
                            stopEnforce(uuid);
                            return;
                        }

                        boolean needs = false;

                        if (doDisplay) {
                            try {
                                String cur = now.getDisplayName();
                                if (cur == null || !cur.equals(desiredColored)) needs = true;
                            } catch (Throwable ignored) {
                                needs = true;
                            }
                        }

                        if (doList) {
                            try {
                                String cur = now.getPlayerListName();
                                if (cur == null || !cur.equals(desiredColored)) needs = true;
                            } catch (Throwable ignored) {
                                needs = true;
                            }
                        }

                        if (needs) {
                            service.applyToPlayer(now, nick);
                        }

                        runs++;
                        if (runs >= maxRuns) stopEnforce(uuid);
                    }
                }, 10L, 10L);

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
