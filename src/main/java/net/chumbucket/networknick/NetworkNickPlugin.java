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

import net.chumbucket.networknick.command.HideCommand;
import net.chumbucket.networknick.command.NickCommand;
import net.chumbucket.networknick.command.ReloadCommand;
import net.chumbucket.networknick.command.UnhideCommand;
import net.chumbucket.networknick.listener.JoinApplyListener;
import net.chumbucket.networknick.listener.PreLoginNickCacheListener;
import net.chumbucket.networknick.papi.NetworkNickExpansion;
import net.chumbucket.networknick.redis.RedisBus;
import net.chumbucket.networknick.service.ExemptService;
import net.chumbucket.networknick.service.NickService;
import net.chumbucket.networknick.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NetworkNickPlugin extends JavaPlugin {

    private RedisBus redis;
    private NickService nickService;
    private ExemptService exemptService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Msg.init(this);

        try {
            RedisLibraries.load(this);
        } catch (Throwable t) {
            getLogger().severe("Failed to load runtime libraries via Libby: " + t.getMessage());
            getLogger().severe("NetworkNick will still enable, but Redis sync will be unavailable.");
        }

        this.nickService = new NickService(this);
        this.redis = new RedisBus(this, nickService);
        this.exemptService = new ExemptService(this);

        // Commands
        if (getCommand("nick") != null) {
            getCommand("nick").setExecutor(new NickCommand(this, nickService, redis, exemptService));
            getCommand("nick").setTabCompleter(new net.chumbucket.networknick.command.NickTabCompleter(nickService));
        }
        if (getCommand("hide") != null) getCommand("hide").setExecutor(new HideCommand(this, nickService, redis));
        if (getCommand("unhide") != null) getCommand("unhide").setExecutor(new UnhideCommand(this, nickService, redis, exemptService));

        if (getCommand("networknick") != null) {
            getCommand("networknick").setExecutor(new ReloadCommand(
                    this,
                    nickService,
                    new ReloadCommand.RedisRef() {
                        @Override public RedisBus get() { return redis; }
                        @Override public void set(RedisBus bus) { redis = bus; }
                    }
            ));
        }

        // Listeners
        Bukkit.getPluginManager().registerEvents(nickService, this);

        // ✅ NEW: pre-login fetch so join message sees displayname nick
        PreLoginNickCacheListener prelogin = new PreLoginNickCacheListener(redis);
        Bukkit.getPluginManager().registerEvents(prelogin, this);

        // ✅ UPDATED: join apply uses prelogin cache and applies at LOWEST priority
        Bukkit.getPluginManager().registerEvents(new JoinApplyListener(this, redis, nickService, prelogin), this);

        // Start redis subscriber
        redis.start();

        // Optional PAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NetworkNickExpansion(this, nickService, redis).register();
            getLogger().info("PlaceholderAPI hooked.");
        }

        getLogger().info("NetworkNick enabled.");
    }

    @Override
    public void onDisable() {
        if (redis != null) redis.stop();
        getLogger().info("NetworkNick disabled.");
    }
}
