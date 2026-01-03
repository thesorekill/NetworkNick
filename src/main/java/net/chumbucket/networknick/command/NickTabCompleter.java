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

import net.chumbucket.networknick.service.NickService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.Pattern;

public final class NickTabCompleter implements TabCompleter {

    private final NickService service;

    public NickTabCompleter(NickService service) {
        this.service = service;
    }

    private static final List<String> CLEAR_WORDS = List.of("off", "reset", "clear");

    // strip legacy &x and §x hex + legacy codes for comparisons
    private static final Pattern AMP_CODE = Pattern.compile("(?i)&[0-9a-fk-or]");
    private static final Pattern SEC_CODE = Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final Pattern HEX_AMP = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern HEX_AMP2 = Pattern.compile("(?i)&x(?:&[0-9a-f]){6}");
    private static final Pattern HEX_SEC2 = Pattern.compile("(?i)§x(?:§[0-9a-f]){6}");

    private static String stripAllCodes(String s) {
        if (s == null) return "";
        s = HEX_SEC2.matcher(s).replaceAll("");
        s = HEX_AMP2.matcher(s).replaceAll("");
        s = HEX_AMP.matcher(s).replaceAll("");
        s = AMP_CODE.matcher(s).replaceAll("");
        s = SEC_CODE.matcher(s).replaceAll("");
        return s;
    }

    private static String norm(String s) {
        return stripAllCodes(s).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isClearWord(String s) {
        if (s == null) return false;
        String t = s.toLowerCase(Locale.ROOT);
        return t.equals("off") || t.equals("reset") || t.equals("clear");
    }

    private static void addIfStartsWith(List<String> out, String candidate, String prefixLower) {
        if (candidate == null || candidate.isBlank()) return;
        if (candidate.toLowerCase(Locale.ROOT).startsWith(prefixLower)) out.add(candidate);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p)) return Collections.emptyList();
        if (!p.hasPermission("networknick.nick")) return Collections.emptyList();

        // /nick <...>
        if (args.length == 1) {
            String prefix = args[0] == null ? "" : args[0];
            String prefixLower = prefix.toLowerCase(Locale.ROOT);
            String prefixNorm = norm(prefix);

            // self clear suggestions (only if allowed)
            List<String> out = new ArrayList<>();
            if (p.hasPermission("networknick.nick.clear")) {
                for (String w : CLEAR_WORDS) addIfStartsWith(out, w, prefixLower);
            }

            // suggest online player REAL names and NICKNAMES if they can manage others (set or clear)
            boolean canOthers = p.hasPermission("networknick.nick.others") || p.hasPermission("networknick.nick.others.clear");
            if (canOthers) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    // real name
                    String real = online.getName();
                    if (real != null && real.toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
                        out.add(real);
                        continue;
                    }

                    // nick name (compare normalized, but suggest the raw visible without codes)
                    String visible = service.getVisibleName(online.getUniqueId(), online.getName());
                    String visPlain = stripAllCodes(visible);

                    if (!prefixNorm.isEmpty() && norm(visible).startsWith(prefixNorm)) {
                        out.add(visPlain);
                    } else if (!prefixNorm.isEmpty() && norm(visPlain).startsWith(prefixNorm)) {
                        out.add(visPlain);
                    }
                }
            }

            // de-dupe, keep stable order
            return dedupe(out);
        }

        // /nick <playerOrNick> <...>
        if (args.length == 2) {
            String first = args[0] == null ? "" : args[0];
            String second = args[1] == null ? "" : args[1];
            String secondLower = second.toLowerCase(Locale.ROOT);

            // If first arg is a clear word, they're probably trying "/nick off <something>" (not supported)
            if (isClearWord(first)) return Collections.emptyList();

            List<String> out = new ArrayList<>();

            // If they can clear others, suggest off/reset/clear for arg2
            if (p.hasPermission("networknick.nick.others.clear")) {
                for (String w : CLEAR_WORDS) addIfStartsWith(out, w, secondLower);
            }

            return dedupe(out);
        }

        return Collections.emptyList();
    }

    private static List<String> dedupe(List<String> in) {
        if (in.isEmpty()) return in;
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return new ArrayList<>(set);
    }
}
