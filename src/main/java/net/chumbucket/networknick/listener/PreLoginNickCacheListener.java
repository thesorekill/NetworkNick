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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetch nick from Redis before join completes so join message plugins
 * that use %player_displayname% see the correct value.
 */
public final class PreLoginNickCacheListener implements Listener {

    private final RedisBus redis;

    // UUID -> nick (null means "no nick")
    private final Map<UUID, String> preloginNick = new ConcurrentHashMap<>();

    public PreLoginNickCacheListener(RedisBus redis) {
        this.redis = redis;
    }

    /**
     * Called on an async thread. Safe to do blocking Redis IO here.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;

        UUID uuid = e.getUniqueId();
        String nick = redis.getNick(uuid); // blocking is OK here (async event)
        if (nick == null || nick.isBlank()) preloginNick.remove(uuid);
        else preloginNick.put(uuid, nick);
    }

    /**
     * Pull nick once on join. Removes entry to prevent leaks.
     */
    public String pop(UUID uuid) {
        if (uuid == null) return null;
        return preloginNick.remove(uuid);
    }

    public void clear(UUID uuid) {
        if (uuid != null) preloginNick.remove(uuid);
    }
}
