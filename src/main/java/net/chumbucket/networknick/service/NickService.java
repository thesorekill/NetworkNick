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

import net.chumbucket.networknick.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NickService implements Listener {

    private final JavaPlugin plugin;

    // What the player should DISPLAY right now (nick or real, including & codes)
    private final Map<UUID, String> liveVisibleName = new ConcurrentHashMap<>();

    // What is STORED as their nick in Redis (null if none). Used to detect hidden (&k...)
    private final Map<UUID, String> storedNick = new ConcurrentHashMap<>();

    public NickService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isHideNick(String nick) {
        if (nick == null) return false;
        String s = nick.trim();
        return s.startsWith("&k") || s.startsWith("§k");
    }

    /**
     * Apply the current nick to a player.
     * @param p player
     * @param nameOrNull the nick stored in Redis (or null to clear)
     */
    public void applyToPlayer(Player p, String nameOrNull) {
        if (nameOrNull == null || nameOrNull.isBlank()) storedNick.remove(p.getUniqueId());
        else storedNick.put(p.getUniqueId(), nameOrNull);

        final String visibleLegacy = (nameOrNull == null || nameOrNull.isBlank())
                ? p.getName()
                : nameOrNull;

        liveVisibleName.put(p.getUniqueId(), visibleLegacy);

        // 1) Display name (older plugins + many PAPI placeholders use this)
        try { p.setDisplayName(Msg.color(visibleLegacy)); }
        catch (Throwable t) { try { p.setDisplayName(visibleLegacy); } catch (Throwable ignored) {} }

        // 2) Player list name (tablist)
        try { p.setPlayerListName(Msg.color(visibleLegacy)); }
        catch (Throwable t) { try { p.setPlayerListName(visibleLegacy); } catch (Throwable ignored) {} }

        // 3) Custom name + visibility (used by some hologram/entity-name plugins; harmless for players)
        try {
            p.setCustomName(Msg.color(visibleLegacy));
            p.setCustomNameVisible(false);
        } catch (Throwable ignored) {}

        // 4) Paper API (if present): displayName(Component) & playerListName(Component)
        // We do this reflectively to keep compiling against Spigot API.
        tryApplyPaperComponents(p, visibleLegacy);

        // NOTE:
        // - You cannot change the real username that %player% resolves to.
        // - But %player_displayname% and most chat/tab systems will now pick up the nick.
    }

    private void tryApplyPaperComponents(Player p, String legacy) {
        try {
            // org.bukkit.entity.Player has these methods on Paper:
            //   void displayName(Component)
            //   void playerListName(Component)
            // Component is from net.kyori.adventure.text.Component (Paper includes it)
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");

            Method compMethod = Msg.class.getMethod("component", String.class);
            Object component = compMethod.invoke(null, legacy);

            // displayName(Component)
            try {
                Method displayName = p.getClass().getMethod("displayName", componentClass);
                displayName.invoke(p, component);
            } catch (NoSuchMethodException ignored) {}

            // playerListName(Component)
            try {
                Method listName = p.getClass().getMethod("playerListName", componentClass);
                listName.invoke(p, component);
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable ignored) {
            // Not Paper or Adventure not present—fine.
        }
    }

    public void clearFromCache(UUID uuid) {
        liveVisibleName.remove(uuid);
        storedNick.remove(uuid);
    }

    /** Visible name: nick if set, otherwise real name (what everyone should see). */
    public String getVisibleName(UUID uuid, String fallbackRealName) {
        String v = liveVisibleName.get(uuid);
        return (v == null || v.isBlank()) ? fallbackRealName : v;
    }

    /** The raw nick stored in Redis (null if none). */
    public String getStoredNick(UUID uuid) {
        return storedNick.get(uuid);
    }

    /** True if the stored nick is an &k hide nick. */
    public boolean isHidden(UUID uuid) {
        return isHideNick(storedNick.get(uuid));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Redis apply happens via JoinApplyListener after join.
        Player p = e.getPlayer();
        liveVisibleName.put(p.getUniqueId(), p.getName());
        storedNick.remove(p.getUniqueId());
    }

    public void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }
}
