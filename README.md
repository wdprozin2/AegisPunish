# 🛡️ AegisPunish — Enterprise-Grade Punishment & Moderation Suite

![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%20%7C%20Folia%20%7C%20Velocity%20%7C%20BungeeCord-blue?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/Minecraft-1.16%20--%2026.3%2B-green?style=for-the-badge)
[![SpigotMC](https://img.shields.io/badge/SpigotMC-139053-ED8106?style=for-the-badge&logo=spigotmc&logoColor=white)](https://www.spigotmc.org/resources/139053/)
[![Modrinth](https://img.shields.io/badge/Modrinth-AegisPunish-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/aegispunish)
[![Wiki](https://img.shields.io/badge/Wiki-Documentation-00d2ff?style=for-the-badge&logo=gitbook&logoColor=white)](https://wdprozin2.github.io/AegisPunish/)
![Java](https://img.shields.io/badge/Java-21%20%7C%2025-orange?style=for-the-badge)
![License](https://img.shields.io/badge/License-GPL--3.0-purple?style=for-the-badge)
![Discord](https://img.shields.io/badge/Discord-wdprozin__-5865F2?style=for-the-badge&logo=discord&logoColor=white)

**AegisPunish** is an enterprise-grade, high-performance punishment and network moderation suite engineered for modern Minecraft networks. Built from the ground up to support everything from single **Paper / Purpur / Folia** servers to massive multi-server networks powering tens of thousands of players across **Velocity** and **BungeeCord** proxies.

Zero lag, sub-millisecond in-memory cache, unbypassable command hijacking, automatic SQLite fallback, auto-updates from Modrinth, and a one-click universal migrator from legacy moderation plugins.

---

## 💬 Community & Links

* **SpigotMC Resource:** [https://www.spigotmc.org/resources/139053/](https://www.spigotmc.org/resources/139053/)
* **Modrinth Project:** [https://modrinth.com/plugin/aegispunish](https://modrinth.com/plugin/aegispunish)
* **Official Documentation (Wiki):** [https://wdprozin2.github.io/AegisPunish/](https://wdprozin2.github.io/AegisPunish/)
* **Discord Contact:** Add **`wdprozin_`** on Discord for direct support, bug reports, and suggestions!

---

## ⚡ Key Highlights

### 🚀 Universal Platform & Version Support
* **Paper / Purpur / Spigot:** Full backwards and forwards compatibility spanning **1.16.5 all the way up to Minecraft 26.3+**.
* **Folia Ready:** Built with thread-safe asynchronous schedulers and region-independent tasking (`folia-supported: true`).
* **Velocity 3.x:** Native proxy integration featuring asynchronous handshake interceptors, pre-login blocking, and limbo redirection.
* **BungeeCord / WaterFall:** Native bungee plugin handling network-wide punishments seamlessly.

### 🔄 Modrinth Auto-Updater (`/aegispunish update`)
* **Zero-Hassle Updates:** Automatically queries Modrinth for new releases on startup.
* **Auto-Download:** When enabled, downloads the updated `.jar` straight into `plugins/update/`, safely applying on the next server reboot.
* **Admin Notifications:** Admins receive a chat alert on join whenever an update is ready.

### ⚖️ Advanced Staff Permission Hierarchy
* **LuckPerms Group & Weight Resolution:** Recursively inspects all inherited groups and custom weights (`aegispunish.weight.<N>`) for both online and offline players.
* **Configurable Fallback Hierarchy:** Define custom group weights directly in `config.yml` (`owner: 100`, `admin: 70`, `mod: 50`, etc.).
* **Anti-Abuse Protection:** Prevents staff members from punishing peers with identical or higher rank (`prevent-equal-rank: true`).

### 💾 Dual Storage Engine (Zero Setup Required)
* **High-Concurrency MySQL / MariaDB:** Powered by HikariCP connection pooling, non-blocking asynchronous execution, and multi-column indexed queries.
* **Smart SQLite Local Fallback:** No MySQL server? No problem! If MySQL credentials are not provided or if your database ever goes down, AegisPunish **automatically falls back to a high-speed local SQLite database (`aegispunish.db`)** with zero downtime and zero data loss.

### 🔄 Universal Legacy Converter (`/aegispunish import`)
Switching from another punishment plugin has never been easier. AegisPunish can migrate all active punishments, historic infractions, and IP histories in seconds from:
* 🟢 **LiteBans** (supports both local `litebans.sqlite` files and MySQL database tables)
* 🔵 **AdvancedBan** (supports both local `AdvancedBan.db` and MySQL tables)
* 🟣 **LeafPunish** (supports both local `database.db` / `leaf.db` and MySQL)
* 🟡 **EssentialsX** (scans all `plugins/Essentials/userdata/*.yml` player profiles)
* ⚪ **Minecraft Vanilla** (`banned-players.json` and `banned-ips.json`)

```shell
/aegispunish import litebans
/aegispunish import advancedban
/aegispunish import leafpunish
/aegispunish import essentials
/aegispunish import vanilla
```

### 🛑 Absolute Command Hierarchy (CommandMap Hijack)
Never worry about players or rogue scripts bypassing moderation commands using prefixes:
* Injects directly into Bukkit's internal `CommandMap` at runtime.
* Hijacks and overrides `/minecraft:ban`, `/minecraft:kick`, `/essentials:ban`, `/essentials:mute`, and vanilla alias variations using `EventPriority.LOWEST` preprocessing.

### 👤 Cross-Platform Skin & Avatar Resolution
Discord webhooks and in-game punishment menus look stunning regardless of client type:
* **Java Mojang Profiles:** Direct high-resolution rendering.
* **Geyser / Floodgate (Bedrock):** Automatically queries Floodgate API, parses Bedrock XUIDs, and displays genuine Bedrock character avatars.
* **SkinsRestorer v15:** Hooks into SkinsRestorer to extract the raw SHA-256 texture hashes, ensuring cracked and custom player skins render with 100% accuracy.

### 💬 Broadcasts & Discord Webhooks
* **Pixel-Perfect Discord Webhooks:** High-fidelity embeds matching modern staff dashboards with player skins, relative duration timestamps (`<t:TIMESTAMP:R>`), reason, proof links, staff member, and server scope.
* **Discord Pardon Embeds:** Automatically dispatches a distinct pink embed whenever a player is unbanned or pardoned.
* **Periodic Broadcasts:** Configurable `[SERVER PUNISHMENTS]` in-game announcements showcasing 24-hour moderation activity with subtle audio cues.

---

## 📋 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/punir [player]` | `aegispunish.command.punir` | Opens the interactive punishment GUI menu. |
| `/ban <player> [reason] [-s]` | `aegispunish.command.ban` | Permanently bans a player (`-s` for silent). |
| `/tempban <player> <time> [reason] [-s]` | `aegispunish.command.tempban` | Temporarily bans a player (e.g. `7d`, `12h`, `30m`). |
| `/ipban <player/IP> [reason] [-s]` | `aegispunish.command.ipban` | Permanently bans an IP address. |
| `/tempipban <player/IP> <time> [reason] [-s]` | `aegispunish.command.tempipban` | Temporarily bans an IP address. |
| `/mute <player> [reason] [-s]` | `aegispunish.command.mute` | Permanently mutes a player in chat. |
| `/tempmute <player> <time> [reason] [-s]` | `aegispunish.command.tempmute` | Temporarily mutes a player. |
| `/warn <player> [reason] [-s]` | `aegispunish.command.warn` | Issues an official warning to a player. |
| `/kick <player> [reason] [-s]` | `aegispunish.command.kick` | Disconnects an active player from the network. |
| `/unban <player/IP>` | `aegispunish.command.unban` | Unbans a player, logs revocation reason & sends Discord webhook. |
| `/unmute <player>` | `aegispunish.command.unmute` | Unmutes a silenced player. |
| `/pardon <id/player>` | `aegispunish.command.pardon` | Universal pardon command for any active punishment. |
| `/historic <player>` | `aegispunish.command.historic` | Opens player infraction history & active records. |
| `/aegis update` / `/aegispunish update` | `aegispunish.admin` | Checks and downloads the latest release from Modrinth. |
| `/aegis reload` / `/aegispunish reload` | `aegispunish.admin` | Reloads configurations and language messages. |
| `/aegis import <source>` / `/aegispunish import` | `aegispunish.admin` | Migrates data from Vanilla, Essentials, LiteBans, AdvancedBan, or LeafPunish. |

---

## 📦 Installation

1. Download the respective JAR for your platform:
   * **`AegisPunish-Paper.jar`** ➔ Drop into your Paper / Purpur / Folia / Spigot `plugins/` directory.
   * **`AegisPunish-Velocity.jar`** ➔ Drop into your Velocity `plugins/` directory.
   * **`AegisPunish-BungeeCord.jar`** ➔ Drop into your BungeeCord / WaterFall `plugins/` directory.
2. Start the server to generate default configurations in `plugins/AegisPunish/config.yml`.
3. *(Optional)* Configure your MySQL connection details. If left default, AegisPunish will immediately use local SQLite.
4. If migrating from an existing plugin, run `/aegis import <plugin>` in console.
5. Enjoy enterprise-grade, lightning-fast moderation!

---

## 📊 Statistics

[![bStats](https://bstats.org/signatures/bukkit/AegisPunish.svg)](https://bstats.org/plugin/bukkit/AegisPunish/34230)

---

## 💡 Credits & Acknowledgements

* **Inspiration:** Special thanks and acknowledgements to **LeafPunish**, which served as a major inspiration for the architecture, features, and concepts behind AegisPunish.

