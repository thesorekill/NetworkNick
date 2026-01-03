# NetworkNick

**NetworkNick** is a lightweight, network-aware nickname plugin for **Paper and Spigot** that provides persistent, Redis-backed nicknames with full support for **PlaceholderAPI**, **LuckPerms**, and modern multi-server environments.

Designed for modern servers and proxy networks, NetworkNick focuses on **correctness, timing, and compatibility** — ensuring nicknames apply **before join messages**, **sync instantly across servers**, and respect **offline permissions**.

---

## 🆕 Latest Release — 1.0.1

### Changelog

**1.0.1**

- Network-wide nicknames via Redis
- Join-time correctness (`%player_displayname%` works in join messages)
- Offline-safe nickname exemption (LuckPerms integration)
- Hide / unhide system with prior nickname restoration
- Robust enforcement against late plugin overrides
- PlaceholderAPI support
- Clean reload support
- Paper & Spigot compatible (no NMS, no ProtocolLib)

---

## Overview

NetworkNick allows players to set nicknames that:

- Persist across restarts and reconnects
- Synchronize instantly across multiple backend servers
- Appear correctly in join messages, chat, TAB, and scoreboards
- Respect permissions, formatting rules, and offline exemptions

The plugin is built around a Redis source of truth, with careful handling of join timing to ensure `%player_displayname%` always reflects the correct nickname.

---

## Core Features

### Nicknames
- Set custom nicknames with `/nick`
- Supports legacy color codes (`&a`) and hex colors (`&#RRGGBB`)
- Optional formatting permissions (`&l`, `&o`, `&k`, etc.)
- Safe length enforcement (3–16 visible characters)

### Hide / Unhide
- `/hide` obfuscates your name using `&k`
- `/unhide` restores your previous nickname or real name
- Prior nicknames are stored safely and restored correctly

### Network-Wide Sync
- Redis-backed nickname storage
- Real-time updates across all servers
- Automatic re-application on join, reload, and reconnect

### Join-Time Correctness
- Nicknames are fetched before login completes
- Applied at LOWEST join priority
- Ensures join messages using `%player_displayname%` show the nickname, not the real name

### Exempt Players (Offline-Safe)
- `networknick.exempt` prevents other players from changing or unhiding your nickname
- Uses the LuckPerms API to check permissions even when the player is offline
- Safe asynchronous handling (no main-thread blocking)

### Enforcement System
- Optional short-term enforcement to beat late plugin overrides
- Protects against plugins that reset display names after join

### Placeholder Support
- PlaceholderAPI integration
- Custom placeholders for network-aware name handling

---

## Requirements

- Java 21
- Paper or Spigot 1.20+
- Redis server (required)
- Optional but recommended: LuckPerms
- Optional: PlaceholderAPI

---

## Installation

### Single-Server Setup

1. Download `NetworkNick-1.0.0.jar`
2. Place it in your server’s `plugins/` directory
3. Start the server
4. Configure permissions (LuckPerms recommended)
5. Restart or run `/networknick reload`

---

### Multi-Server / Network Setup

Recommended for Velocity or BungeeCord environments.

**Requirements**
- Shared Redis instance
- Identical plugin configuration across all backend servers

**Steps**
1. Configure Redis connectivity
2. Ensure all backend servers connect to the same Redis instance
3. Restart all servers

**What you get**
- Network-wide nicknames
- Instant synchronization across servers
- Consistent join messages everywhere

Redis acts as the source of truth  
Local caches are ephemeral and safe

---

## Commands

### Player Commands

| Command | Description | Permission |
|-------|-------------|------------|
| `/nick <name>` | Set your nickname | `networknick.nick` |
| `/nick off` | Clear your nickname | `networknick.nick.clear` |
| `/hide` | Hide your name | `networknick.hide` |
| `/unhide` | Restore your name | `networknick.unhide` |

### Moderation / Admin Commands

| Command | Description | Permission |
|-------|-------------|------------|
| `/nick <player> <name>` | Set another player’s nickname | `networknick.nick.others` |
| `/nick <player> off` | Clear another player’s nickname | `networknick.nick.others.clear` |
| `/unhide <player>` | Unhide another player | `networknick.unhide.others` |
| `/networknick reload` | Reload config and Redis | `networknick.reload` |

---

## Permissions

### Core

| Permission | Description | Default |
|-----------|-------------|---------|
| `networknick.nick` | Use `/nick` | op |
| `networknick.nick.clear` | Clear own nickname | op |
| `networknick.hide` | Use `/hide` | op |
| `networknick.unhide` | Use `/unhide` | op |

### Others / Moderation

| Permission | Description |
|-----------|-------------|
| `networknick.nick.others` | Set others’ nicknames |
| `networknick.nick.others.clear` | Clear others’ nicknames |
| `networknick.unhide.others` | Unhide others |

### Formatting

| Permission | Description |
|-----------|-------------|
| `networknick.nick.colors` | Color and hex codes |
| `networknick.nick.format.k` | Obfuscated |
| `networknick.nick.format.l` | Bold |
| `networknick.nick.format.m` | Strikethrough |
| `networknick.nick.format.n` | Underline |
| `networknick.nick.format.o` | Italic |
| `networknick.nick.format.r` | Reset |

### Protection

| Permission | Description |
|-----------|-------------|
| `networknick.exempt` | Prevent others from changing or unhiding your nickname |

---

## Placeholders (PlaceholderAPI)

| Placeholder | Description |
|------------|-------------|
| `%networknick_name%` | Visible nickname (or real name) |
| `%networknick_unhidden%` | Real name or prior nickname |
| `%networknick_hidden%` | `true` or `false` |

Note: `%player_displayname%` will also reflect the nickname, as NetworkNick applies it before join messages are processed. Just make sure if you're using essentials you have change-displayname set to false in their config.

---

## Planned Enhancements

- None currently. Open to suggestions

---

## License

Licensed under the Apache License, Version 2.0  
See `LICENSE` for details.

---

## Credits

Developed by **Chumbucket**  
Built for the **Chumbucket Network**  
https://www.chumbucket.net

© 2025 Chumbucket — Apache-2.0 License
