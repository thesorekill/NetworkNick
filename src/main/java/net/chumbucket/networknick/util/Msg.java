/*
 * Copyright © 2025 Sorekill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package net.chumbucket.networknick.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class Msg {

    private static JavaPlugin plugin;

    // Supports legacy & color codes -> Component
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private Msg() {}

    public static void init(JavaPlugin pl) {
        plugin = pl;
    }

    public static String color(String s) {
        if (s == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }

    public static Component component(String legacy) {
        if (legacy == null) return Component.empty();
        return LEGACY.deserialize(color(legacy));
    }

    public static void send(CommandSender to, String path, String... pairs) {
        if (plugin == null) return;

        String msg = plugin.getConfig().getString(path, "");
        if (msg == null || msg.isBlank()) return;

        for (int i = 0; i + 1 < pairs.length; i += 2) {
            msg = msg.replace(pairs[i], pairs[i + 1]);
        }
        if (msg == null || msg.isBlank()) return;

        to.sendMessage(color(msg));
    }
}
