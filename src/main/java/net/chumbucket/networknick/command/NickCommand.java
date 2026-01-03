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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NickCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final NickService service;
    private final RedisBus redis;
    private final ExemptService exempt;

    public NickCommand(JavaPlugin plugin, NickService service, RedisBus redis, ExemptService exempt) {
        this.plugin = plugin;
        this.service = service;
        this.redis = redis;
        this.exempt = exempt;
    }

    private static final Pattern ALLOWED_TOKENS = Pattern.compile(
            "(?i)" +
                    "(?:&#[0-9a-f]{6})" +
                    "|(?:&x(?:&[0-9a-f]){6})" +
                    "|(?:&[0-9a-fk-or])" +
                    "|(?:[A-Za-z0-9_])"
    );

    private static final Pattern LEGACY_CODE_AMP = Pattern.compile("(?i)&[0-9a-fk-or]");
    private static final Pattern LEGACY_CODE_SEC = Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final Pattern HEX_1 = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern HEX_2 = Pattern.compile("(?i)&x(?:&[0-9a-f]){6}");
    private static final Pattern HEX_2_SEC = Pattern.compile("(?i)§x(?:§[0-9a-f]){6}");

    private static boolean isLegacyColorCode(String token) {
        return token.length() == 2
                && token.charAt(0) == '&'
                && "0123456789abcdef".indexOf(Character.toLowerCase(token.charAt(1))) >= 0;
    }

    private static boolean isClearWord(String s) {
        if (s == null) return false;
        return s.equalsIgnoreCase("off")
                || s.equalsIgnoreCase("reset")
                || s.equalsIgnoreCase("clear");
    }

    private static String stripAllCodes(String s) {
        if (s == null) return "";
        s = HEX_2_SEC.matcher(s).replaceAll("");
        s = HEX_2.matcher(s).replaceAll("");
        s = HEX_1.matcher(s).replaceAll("");
        s = LEGACY_CODE_AMP.matcher(s).replaceAll("");
        s = LEGACY_CODE_SEC.matcher(s).replaceAll("");
        return s;
    }

    private static String normalizeCompare(String s) {
        String t = stripAllCodes(s);
        t = t.trim().toLowerCase(Locale.ROOT);
        return t;
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
                if (i + 1 < rawWithCodes.length()) {
                    String maybeLegacy = rawWithCodes.substring(i, i + 2);
                    if (LEGACY_CODE_AMP.matcher(maybeLegacy).matches()) {
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

    private boolean validateAllowedCharacters(String input) {
        if (input == null || input.isBlank()) return false;

        int idx = 0;
        while (idx < input.length()) {
            Matcher m = ALLOWED_TOKENS.matcher(input);
            m.region(idx, input.length());
            if (!m.lookingAt()) return false;
            idx = m.end();
        }
        return true;
    }

    private boolean hasAnyColorOrFormatCodes(String s) {
        if (s == null) return false;
        return HEX_1.matcher(s).find() || HEX_2.matcher(s).find() || LEGACY_CODE_AMP.matcher(s).find();
    }

    private boolean checkPermissionsForCodes(Player p, String nick) {
        boolean containsHex = HEX_1.matcher(nick).find() || HEX_2.matcher(nick).find();

        boolean containsLegacyColor = false;
        Matcher legacy = LEGACY_CODE_AMP.matcher(nick);
        while (legacy.find()) {
            String code = legacy.group();
            if (isLegacyColorCode(code)) {
                containsLegacyColor = true;
                break;
            }
        }

        if ((containsHex || containsLegacyColor) && !p.hasPermission("networknick.nick.colors")) return false;

        if (Pattern.compile("(?i)&l").matcher(nick).find() && !p.hasPermission("networknick.nick.format.l")) return false;
        if (Pattern.compile("(?i)&m").matcher(nick).find() && !p.hasPermission("networknick.nick.format.m")) return false;
        if (Pattern.compile("(?i)&n").matcher(nick).find() && !p.hasPermission("networknick.nick.format.n")) return false;
        if (Pattern.compile("(?i)&o").matcher(nick).find() && !p.hasPermission("networknick.nick.format.o")) return false;
        if (Pattern.compile("(?i)&r").matcher(nick).find() && !p.hasPermission("networknick.nick.format.r")) return false;
        if (Pattern.compile("(?i)&k").matcher(nick).find() && !p.hasPermission("networknick.nick.format.k")) return false;

        return true;
    }

    private void clearNick(UUID targetUuid) {
        redis.setNick(targetUuid, null);
        try { redis.clearPriorNick(targetUuid); } catch (Throwable ignored) {}
    }

    private OfflinePlayer resolveTargetByNameOrNick(String input) {
        String raw = input.trim();
        if (raw.isEmpty()) return null;

        Player exact = Bukkit.getPlayerExact(raw);
        if (exact != null) return exact;

        String wanted = normalizeCompare(raw);

        List<Player> exactNickMatches = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            String visible = service.getVisibleName(online.getUniqueId(), online.getName());
            if (normalizeCompare(visible).equals(wanted)) exactNickMatches.add(online);
        }
        if (!exactNickMatches.isEmpty()) return exactNickMatches.get(0);

        List<Player> partialMatches = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            String visible = service.getVisibleName(online.getUniqueId(), online.getName());
            String norm = normalizeCompare(visible);
            if (!wanted.isEmpty() && norm.contains(wanted)) partialMatches.add(online);
        }
        if (partialMatches.size() == 1) return partialMatches.get(0);

        try {
            UUID u = UUID.fromString(raw);
            return Bukkit.getOfflinePlayer(u);
        } catch (IllegalArgumentException ignored) {}

        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(raw);
        return op;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("networknick.nick")) {
            Msg.send(p, "messages.no-perms");
            return true;
        }

        if (args.length == 0) {
            p.sendMessage(Msg.color("&cUsage: /nick <name|off|reset|clear>"));
            p.sendMessage(Msg.color("&c       /nick <playerOrNick> <name|off|reset|clear>"));
            return true;
        }

        // SELF: /nick <name|off|reset|clear>
        if (args.length == 1) {
            String token = args[0].trim();

            if (isClearWord(token)) {
                if (!p.hasPermission("networknick.nick.clear")) {
                    Msg.send(p, "messages.no-perms");
                    return true;
                }
                clearNick(p.getUniqueId());
                Msg.send(p, "messages.nick-cleared");
                return true;
            }

            String newNick = token;

            if (!validateAllowedCharacters(newNick)) {
                Msg.send(p, "messages.invalid");
                return true;
            }

            if (hasAnyColorOrFormatCodes(newNick) && !checkPermissionsForCodes(p, newNick)) {
                p.sendMessage(Msg.color("&cYou don't have permission to use those nickname styles."));
                return true;
            }

            int maxLen = plugin.getConfig().getInt("nick.max-length", 16);
            if (maxLen < 3) maxLen = 3;
            if (maxLen > 16) maxLen = 16;

            String trimmed = trimToVisibleLength(newNick, maxLen);
            String visibleOnly = stripAllCodes(trimmed);

            if (visibleOnly.length() < 3) {
                Msg.send(p, "messages.invalid");
                return true;
            }

            redis.setNick(p.getUniqueId(), trimmed);
            Msg.send(p, "messages.nick-set", "{nick}", trimmed);
            return true;
        }

        // OTHERS: /nick <playerOrNick> <name|off|reset|clear>
        if (args.length >= 2) {
            String targetArg = args[0].trim();
            String valueArg = args[1].trim();

            OfflinePlayer target = resolveTargetByNameOrNick(targetArg);
            if (target == null) {
                Msg.send(p, "messages.invalid");
                return true;
            }

            UUID targetUuid = target.getUniqueId();
            String targetName = (target.getName() == null) ? targetArg : target.getName();

            // ✅ Offline-capable exempt gate (async check; callback runs sync)
            exempt.isExempt(targetUuid, isExempt -> {
                if (!targetUuid.equals(p.getUniqueId()) && isExempt) {
                    p.sendMessage(Msg.color("&cThat player is nickname-exempt."));
                    return;
                }

                // Clear others
                if (isClearWord(valueArg)) {
                    if (!p.hasPermission("networknick.nick.others.clear")) {
                        Msg.send(p, "messages.no-perms");
                        return;
                    }

                    clearNick(targetUuid);
                    p.sendMessage(Msg.color("&aCleared &f" + targetName + "&a's nickname."));
                    return;
                }

                // Set others
                if (!p.hasPermission("networknick.nick.others")) {
                    Msg.send(p, "messages.no-perms");
                    return;
                }

                String newNick = valueArg;

                if (!validateAllowedCharacters(newNick)) {
                    Msg.send(p, "messages.invalid");
                    return;
                }

                if (hasAnyColorOrFormatCodes(newNick) && !checkPermissionsForCodes(p, newNick)) {
                    p.sendMessage(Msg.color("&cYou don't have permission to use those nickname styles."));
                    return;
                }

                int maxLen = plugin.getConfig().getInt("nick.max-length", 16);
                if (maxLen < 3) maxLen = 3;
                if (maxLen > 16) maxLen = 16;

                String trimmed = trimToVisibleLength(newNick, maxLen);
                String visibleOnly = stripAllCodes(trimmed);

                if (visibleOnly.length() < 3) {
                    Msg.send(p, "messages.invalid");
                    return;
                }

                redis.setNick(targetUuid, trimmed);
                p.sendMessage(Msg.color("&aSet &f" + targetName + "&a to &f" + trimmed + "&a."));
            });

            return true;
        }

        p.sendMessage(Msg.color("&cUsage: /nick <name|off|reset|clear>"));
        p.sendMessage(Msg.color("&c       /nick <playerOrNick> <name|off|reset|clear>"));
        return true;
    }
}
