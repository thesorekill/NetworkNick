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
import net.chumbucket.networknick.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.regex.Pattern;

public final class HideCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final RedisBus redis;

    public HideCommand(JavaPlugin plugin,
                       net.chumbucket.networknick.service.NickService service, // kept for call-site compatibility
                       RedisBus redis) {
        this.plugin = plugin;
        this.redis = redis;
    }

    private static boolean isHideNick(String nick) {
        if (nick == null) return false;
        String s = nick.trim();
        // could be stored as &k... or already colorized as §k...
        return s.startsWith("&k") || s.startsWith("§k");
    }

    // ---- code/length helpers (kept local; no dependency on NickCommand) ----

    private static final Pattern HEX_1 = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern HEX_2 = Pattern.compile("(?i)&x(?:&[0-9a-f]){6}");
    private static final Pattern LEGACY_AMP = Pattern.compile("(?i)&[0-9a-fk-or]");

    private static String stripAllCodes(String s) {
        if (s == null) return "";
        // &x&1&2&3&4&5&6
        s = HEX_2.matcher(s).replaceAll("");
        // &#RRGGBB
        s = HEX_1.matcher(s).replaceAll("");
        // legacy &a, &k, etc
        s = LEGACY_AMP.matcher(s).replaceAll("");
        // handle § codes if pasted in
        s = s.replaceAll("(?i)§x(?:§[0-9a-f]){6}", "");
        s = s.replaceAll("(?i)§[0-9a-fk-or]", "");
        return s;
    }

    /**
     * Trim a legacy string with & codes to a maximum visible character count.
     * Keeps color/format tokens intact while trimming only visible characters.
     */
    private static String trimToVisibleLength(String rawWithCodes, int maxVisible) {
        if (rawWithCodes == null) return "";
        if (maxVisible <= 0) return "";

        StringBuilder out = new StringBuilder(rawWithCodes.length());
        int visible = 0;

        int i = 0;
        while (i < rawWithCodes.length()) {
            char c = rawWithCodes.charAt(i);

            if (c == '&') {
                // &#RRGGBB
                if (i + 8 <= rawWithCodes.length()) {
                    String maybeHex1 = rawWithCodes.substring(i, i + 8);
                    if (HEX_1.matcher(maybeHex1).matches()) {
                        out.append(maybeHex1);
                        i += 8;
                        continue;
                    }
                }
                // &x&1&2&3&4&5&6
                if (i + 14 <= rawWithCodes.length()) {
                    String maybeHex2 = rawWithCodes.substring(i, i + 14);
                    if (HEX_2.matcher(maybeHex2).matches()) {
                        out.append(maybeHex2);
                        i += 14;
                        continue;
                    }
                }
                // &a / &k / etc
                if (i + 2 <= rawWithCodes.length()) {
                    String maybeLegacy = rawWithCodes.substring(i, i + 2);
                    if (LEGACY_AMP.matcher(maybeLegacy).matches()) {
                        out.append(maybeLegacy);
                        i += 2;
                        continue;
                    }
                }
            }

            out.append(c);
            visible++;
            if (visible >= maxVisible) break;
            i++;
        }

        return out.toString();
    }

    /**
     * Returns the configured hide nickname (hide.nick) if present, else null.
     * - Ensures it starts with &k/§k so hide detection works.
     * - Trims to nick.max-length visible chars for safety.
     */
    private String getConfiguredHideNickOrNull(int maxVisibleLen) {
        String cfg = plugin.getConfig().getString("hide.nick", "");
        if (cfg == null) return null;

        cfg = cfg.trim();
        if (cfg.isBlank()) return null;

        if (!(cfg.startsWith("&k") || cfg.startsWith("§k"))) {
            cfg = "&k" + cfg;
        }

        String trimmed = trimToVisibleLength(cfg, maxVisibleLen);

        // If trimming destroys visible characters completely, ignore and fallback
        String visibleOnly = stripAllCodes(trimmed);
        if (visibleOnly.isBlank()) return null;

        return trimmed;
    }

    private int getMaxVisibleNickLen() {
        int maxLen = plugin.getConfig().getInt("nick.max-length", 16);
        if (maxLen < 3) maxLen = 3;
        if (maxLen > 16) maxLen = 16;
        return maxLen;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("networknick.hide")) {
            Msg.send(p, "messages.no-perms");
            return true;
        }

        // Toggle behavior: if already hidden -> unhide (restore prior nick if any)
        String current = redis.getNick(p.getUniqueId());
        if (isHideNick(current)) {
            String prior = redis.getPriorNick(p.getUniqueId());

            if (prior != null && !prior.isBlank()) {
                redis.setNick(p.getUniqueId(), prior);
                redis.clearPriorNick(p.getUniqueId());
                Msg.send(p, "messages.unhide", "{nick}", prior);
            } else {
                redis.setNick(p.getUniqueId(), null);
                redis.clearPriorNick(p.getUniqueId());
                Msg.send(p, "messages.unhide", "{nick}", p.getName());
            }
            return true;
        }

        // Not currently hidden -> hide them

        int maxVisibleLen = getMaxVisibleNickLen();

        // BEFORE hiding: store current nick if it's a real nick (not blank, not already hide)
        if (current != null && !current.isBlank() && !isHideNick(current)) {
            redis.setPriorNick(p.getUniqueId(), current);
        } else {
            // If they had no nick, keep prior cleared so unhide returns to normal name
            redis.clearPriorNick(p.getUniqueId());
        }

        // 1) Prefer config-defined hide nick if present
        String configured = getConfiguredHideNickOrNull(maxVisibleLen);
        if (configured != null) {
            redis.setNick(p.getUniqueId(), configured);
            Msg.send(p, "messages.hide-set", "{nick}", configured);
            return true;
        }

        // 2) Fallback: generate "&k" + ":" repeated hide.random-length (capped to visible max)
        int len = plugin.getConfig().getInt("hide.random-length", 12);
        if (len < 3) len = 3;
        if (len > maxVisibleLen) len = maxVisibleLen;

        String body = ":".repeat(len);
        String nick = "&k" + body;

        redis.setNick(p.getUniqueId(), nick);
        Msg.send(p, "messages.hide-set", "{nick}", nick);
        return true;
    }
}
