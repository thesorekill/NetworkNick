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
import net.chumbucket.networknick.service.ExemptService;
import net.chumbucket.networknick.service.NickService;
import net.chumbucket.networknick.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class UnhideCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final NickService service;
    private final RedisBus redis;
    private final ExemptService exempt;

    public UnhideCommand(JavaPlugin plugin, NickService service, RedisBus redis, ExemptService exempt) {
        this.plugin = plugin;
        this.service = service;
        this.redis = redis;
        this.exempt = exempt;
    }

    private static boolean isHideNick(String nick) {
        if (nick == null) return false;
        String s = nick.trim();
        return s.startsWith("&k") || s.startsWith("§k");
    }

    // --- compare helpers (keep simple, but support stripping hex too) ---

    private static final Pattern HEX_1 = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern HEX_2 = Pattern.compile("(?i)&x(?:&[0-9a-f]){6}");
    private static final Pattern LEGACY_AMP = Pattern.compile("(?i)&[0-9a-fk-or]");

    private static String stripAllCodesForCompare(String s) {
        if (s == null) return "";
        s = HEX_2.matcher(s).replaceAll("");
        s = HEX_1.matcher(s).replaceAll("");
        s = LEGACY_AMP.matcher(s).replaceAll("");
        s = s.replaceAll("(?i)§x(?:§[0-9a-f]){6}", "");
        s = s.replaceAll("(?i)§[0-9a-fk-or]", "");
        return s.trim().toLowerCase(Locale.ROOT);
    }

    // --- hide nick config support (same behavior as HideCommand) ---

    private static String stripAllCodes(String s) {
        if (s == null) return "";
        s = HEX_2.matcher(s).replaceAll("");
        s = HEX_1.matcher(s).replaceAll("");
        s = LEGACY_AMP.matcher(s).replaceAll("");
        s = s.replaceAll("(?i)§x(?:§[0-9a-f]){6}", "");
        s = s.replaceAll("(?i)§[0-9a-fk-or]", "");
        return s;
    }

    private static String trimToVisibleLength(String rawWithCodes, int maxVisible) {
        if (rawWithCodes == null) return "";
        if (maxVisible <= 0) return "";

        StringBuilder out = new StringBuilder(rawWithCodes.length());
        int visible = 0;

        int i = 0;
        while (i < rawWithCodes.length()) {
            char c = rawWithCodes.charAt(i);

            if (c == '&') {
                if (i + 8 <= rawWithCodes.length()) {
                    String maybeHex1 = rawWithCodes.substring(i, i + 8);
                    if (HEX_1.matcher(maybeHex1).matches()) {
                        out.append(maybeHex1);
                        i += 8;
                        continue;
                    }
                }
                if (i + 14 <= rawWithCodes.length()) {
                    String maybeHex2 = rawWithCodes.substring(i, i + 14);
                    if (HEX_2.matcher(maybeHex2).matches()) {
                        out.append(maybeHex2);
                        i += 14;
                        continue;
                    }
                }
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

    private int getMaxVisibleNickLen() {
        int maxLen = plugin.getConfig().getInt("nick.max-length", 16);
        if (maxLen < 3) maxLen = 3;
        if (maxLen > 16) maxLen = 16;
        return maxLen;
    }

    private String getConfiguredHideNickOrNull(int maxVisibleLen) {
        String cfg = plugin.getConfig().getString("hide.nick", "");
        if (cfg == null) return null;

        cfg = cfg.trim();
        if (cfg.isBlank()) return null;

        if (!(cfg.startsWith("&k") || cfg.startsWith("§k"))) {
            cfg = "&k" + cfg;
        }

        String trimmed = trimToVisibleLength(cfg, maxVisibleLen);
        String visibleOnly = stripAllCodes(trimmed);
        if (visibleOnly.isBlank()) return null;

        return trimmed;
    }

    private OfflinePlayer resolveTargetByNameOrNick(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) return null;

        Player exact = Bukkit.getPlayerExact(raw);
        if (exact != null) return exact;

        String wanted = stripAllCodesForCompare(raw);

        List<Player> exactNick = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            String visible = service.getVisibleName(online.getUniqueId(), online.getName());
            if (stripAllCodesForCompare(visible).equals(wanted)) exactNick.add(online);
        }
        if (!exactNick.isEmpty()) return exactNick.get(0);

        List<Player> partial = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            String visible = service.getVisibleName(online.getUniqueId(), online.getName());
            String norm = stripAllCodesForCompare(visible);
            if (!wanted.isEmpty() && norm.contains(wanted)) partial.add(online);
        }
        if (partial.size() == 1) return partial.get(0);

        try {
            UUID u = UUID.fromString(raw);
            return Bukkit.getOfflinePlayer(u);
        } catch (IllegalArgumentException ignored) {}

        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(raw);
        return op;
    }

    private void doUnhide(UUID uuid, String fallbackName, CommandSender actor) {
        String current = redis.getNick(uuid);

        if (!isHideNick(current)) {
            actor.sendMessage(Msg.color("&cThat player is not hidden."));
            return;
        }

        String prior = redis.getPriorNick(uuid);

        if (prior != null && !prior.isBlank()) {
            redis.setNick(uuid, prior);
            redis.clearPriorNick(uuid);
            actor.sendMessage(Msg.color("&aUnhid &f" + fallbackName + "&a (restored &f" + prior + "&a)."));
        } else {
            redis.setNick(uuid, null);
            redis.clearPriorNick(uuid);
            actor.sendMessage(Msg.color("&aUnhid &f" + fallbackName + "&a (restored normal name)."));
        }
    }

    private void doHideSelf(Player p, String currentNick) {
        int maxVisibleLen = getMaxVisibleNickLen();

        // Store current nick as prior if it's a real nick (not blank, not already hide)
        if (currentNick != null && !currentNick.isBlank() && !isHideNick(currentNick)) {
            redis.setPriorNick(p.getUniqueId(), currentNick);
        } else {
            redis.clearPriorNick(p.getUniqueId());
        }

        // Prefer config-driven hide nick
        String configured = getConfiguredHideNickOrNull(maxVisibleLen);
        if (configured != null) {
            redis.setNick(p.getUniqueId(), configured);
            Msg.send(p, "messages.hide-set", "{nick}", configured);
            return;
        }

        // Fallback generator
        int len = plugin.getConfig().getInt("hide.random-length", 12);
        if (len < 3) len = 3;
        if (len > maxVisibleLen) len = maxVisibleLen;

        String body = ":".repeat(len);
        String nick = "&k" + body;

        redis.setNick(p.getUniqueId(), nick);
        Msg.send(p, "messages.hide-set", "{nick}", nick);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // SELF: /unhide
        if (args.length == 0) {
            if (!p.hasPermission("networknick.unhide")) {
                Msg.send(p, "messages.no-perms");
                return true;
            }

            String current = redis.getNick(p.getUniqueId());

            // Unhide self if hidden
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

            // Not hidden -> behave like toggle hide (same as /hide)
            if (!p.hasPermission("networknick.hide")) {
                Msg.send(p, "messages.no-perms");
                return true;
            }

            doHideSelf(p, current);
            return true;
        }

        // OTHERS: /unhide <playerOrNick>
        if (!p.hasPermission("networknick.unhide.others")) {
            Msg.send(p, "messages.no-perms");
            return true;
        }

        OfflinePlayer target = resolveTargetByNameOrNick(args[0]);
        if (target == null) {
            p.sendMessage(Msg.color("&cPlayer not found."));
            return true;
        }

        UUID targetUuid = target.getUniqueId();
        String targetName = (target.getName() == null) ? args[0] : target.getName();

        // Offline-capable exempt gate (async check; callback runs sync)
        exempt.isExempt(targetUuid, isExempt -> {
            if (!targetUuid.equals(p.getUniqueId()) && isExempt) {
                p.sendMessage(Msg.color("&cThat player is nickname-exempt."));
                return;
            }
            doUnhide(targetUuid, targetName, p);
        });

        return true;
    }
}
