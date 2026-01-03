/*
 * Copyright © 2025 Sorekill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package net.chumbucket.networknick;

import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import org.bukkit.plugin.java.JavaPlugin;

public final class RedisLibraries {

    private RedisLibraries() {}

    public static void load(JavaPlugin plugin) {
        BukkitLibraryManager manager = new BukkitLibraryManager(plugin);

        // If you want to add repositories, Libby supports it, but Maven Central is enough for Jedis.
        // manager.addRepository("https://repo1.maven.org/maven2/");

        // ✅ Load Jedis at runtime (and transitives like commons-pool2)
        manager.loadLibrary(Library.builder()
                .groupId("redis.clients")
                .artifactId("jedis")
                .version("5.2.0")
                .build());
    }
}
