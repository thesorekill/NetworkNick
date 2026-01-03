/*
 * Copyright © 2025 Sorekill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package net.chumbucket.networknick.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.chumbucket.networknick.redis.RedisBus;
import net.chumbucket.networknick.service.NickService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class NetworkNickExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final NickService service;
    private final RedisBus redis;

    public NetworkNickExpansion(JavaPlugin plugin, NickService service, RedisBus redis) {
        this.plugin = plugin;
        this.service = service;
        this.redis = redis;
    }

    @Override public String getIdentifier() { return "networknick"; }
    @Override public String getAuthor() { return "Chumbucket"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public String onPlaceholderRequest(Player p, String params) {
        if (p == null) return "";
        UUID uuid = p.getUniqueId();

        if (params.equalsIgnoreCase("name")) {
            // What everyone should see (nick or real), including hidden &k if hidden
            return service.getVisibleName(uuid, p.getName());
        }

        if (params.equalsIgnoreCase("unhidden")) {
            // If hidden, show prior nick (if exists), else show real name
            if (service.isHidden(uuid)) {
                String prior = redis.getPriorNick(uuid);
                if (prior != null && !prior.isBlank()) return prior;
                return p.getName();
            }
            // Not hidden -> just normal visible
            return service.getVisibleName(uuid, p.getName());
        }

        if (params.equalsIgnoreCase("hidden")) {
            return service.isHidden(uuid) ? "true" : "false";
        }

        return "";
    }
}
