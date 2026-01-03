/*
 * Copyright © 2025 Sorekill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package net.chumbucket.networknick.service;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Checks if a UUID is nickname-exempt, INCLUDING offline players (via LuckPerms if present).
 *
 * Permission: networknick.exempt
 *
 * Behavior:
 * - If LuckPerms is installed: uses LP to check permission for offline/online UUIDs (async).
 * - If not installed: can only reliably check online players.
 */
public final class ExemptService {

    private static final String EXEMPT_NODE = "networknick.exempt";

    private final JavaPlugin plugin;
    private final boolean luckPermsPresent;
    private final LuckPerms lp;

    public ExemptService(JavaPlugin plugin) {
        this.plugin = plugin;

        LuckPerms found = null;
        boolean present = false;
        try {
            // only valid if LuckPerms is installed
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                found = LuckPermsProvider.get();
                present = (found != null);
            }
        } catch (Throwable ignored) {
            // LuckPerms not installed or API not available
        }

        this.luckPermsPresent = present;
        this.lp = found;

        if (luckPermsPresent) {
            plugin.getLogger().info("LuckPerms detected - offline exempt checks enabled.");
        } else {
            plugin.getLogger().info("LuckPerms not detected - exempt checks are online-only.");
        }
    }

    /**
     * Async exempt check. The callback is always executed on the main thread.
     */
    public void isExempt(UUID uuid, Consumer<Boolean> callback) {
        if (uuid == null) {
            runSync(() -> callback.accept(false));
            return;
        }

        if (!luckPermsPresent || lp == null) {
            // Fallback: only online check
            Player p = Bukkit.getPlayer(uuid);
            boolean exempt = (p != null && p.isOnline() && p.hasPermission(EXEMPT_NODE));
            runSync(() -> callback.accept(exempt));
            return;
        }

        CompletableFuture<User> fut = lp.getUserManager().loadUser(uuid);

        fut.handle((user, err) -> {
            boolean exempt = false;

            try {
                if (err == null && user != null) {
                    QueryOptions qo = lp.getContextManager()
                            .getQueryOptions(user)
                            .orElse(lp.getContextManager().getStaticQueryOptions());

                    exempt = user.getCachedData()
                            .getPermissionData(qo)
                            .checkPermission(EXEMPT_NODE)
                            .asBoolean();
                } else {
                    // If LP failed, fallback online check
                    Player p = Bukkit.getPlayer(uuid);
                    exempt = (p != null && p.isOnline() && p.hasPermission(EXEMPT_NODE));
                }
            } catch (Throwable t) {
                // fallback online check on any weird LP issue
                Player p = Bukkit.getPlayer(uuid);
                exempt = (p != null && p.isOnline() && p.hasPermission(EXEMPT_NODE));
            }

            final boolean finalExempt = exempt;
            runSync(() -> callback.accept(finalExempt));
            return null;
        });
    }

    private void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }
}
