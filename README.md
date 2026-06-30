<div align="center">

# 🛡️ GoidaInvRestore

**Server-side inventory insurance for NeoForge.**
Periodic and on-death snapshots, a paginated in-game restore GUI, MySQL or file storage — no third-party tools, no manual NBT surgery.

[![Latest Release](https://img.shields.io/github/v/release/Yukovsky/GoidaInvRestore?style=for-the-badge&label=latest&color=brightgreen&logo=github)](https://github.com/Yukovsky/GoidaInvRestore/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.233+-F16436?style=for-the-badge)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue?style=for-the-badge)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/Yukovsky/GoidaInvRestore/build.yml?style=for-the-badge&label=build)](https://github.com/Yukovsky/GoidaInvRestore/actions)

[**Discord**](https://discord.gg/prJwFwy5ns) · [**Issues**](https://github.com/Yukovsky/GoidaInvRestore/issues) · [**Releases**](https://github.com/Yukovsky/GoidaInvRestore/releases)

</div>

<br>

> "Can you give back my items, I died / got griefed / duped a chest" is a routine support ticket on any survival server. GoidaInvRestore turns it into a 10-second admin command — every backup is timestamped, indexed per player, and viewable before it's applied.

<br>

## Table of Contents

- [What gets captured](#-what-gets-captured)
- [Admin GUI & Commands](#-admin-gui--commands)
- [Storage backends](#-storage-backends)
- [Compatibility](#-compatibility)
- [Requirements](#-requirements)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Building from Source](#-building-from-source)
- [License](#-license)

<br>

## 📸 What gets captured

| Source | Trigger | Notes |
|:---|:---|:---|
| **Periodic** | Configurable interval (default `30m`) | Skipped automatically if nothing changed since the last snapshot |
| **Death** | Before items hit the ground | Captures the exact loadout that was lost |
| **Logout** | Optional, off by default | |
| **Manual** | `/goidainvrestore backup <player>` | Staff-triggered, on demand |
| **Pre-restore safety net** | Automatic, on by default | Every restore first backs up the *current* state — restores are themselves reversible |

Each snapshot covers main inventory, armor, off-hand, ender chest, and experience — plus Curios slots, Cosmetic Armor Reworked layers, and equipped Sophisticated Backpacks contents when those mods are present.

History is a per-player ring buffer (`maxRecordsPerPlayer`, default `20`) — old snapshots roll off automatically, storage never grows unbounded.

<br>

## 🎮 Admin GUI & Commands

History and individual snapshots open as a real paginated chest GUI — click an entry to inspect it, no chat-typed indices required for browsing.

```
/goidainvrestore backups <player>                   open the paginated backup history GUI
/goidainvrestore view <player> <index>               inspect one snapshot's contents
/goidainvrestore backup <player>                     take a manual backup now
/goidainvrestore restore <player> <index>            restore, overwriting current items (default)
/goidainvrestore restore <player> <index> overwrite  same as above, explicit
/goidainvrestore restore <player> <index> give       give snapshot items without clearing first
/goidainvrestore delete <player> <index>             remove a single backup
/goidainvrestore clear <player>                       wipe a player's entire history
```

`/invrestore` is a registered alias. All access is gated by permission nodes — NeoForge `PermissionAPI` (LuckPerms-as-a-mod), with FTB Ranks and Bukkit-hybrid-core fallbacks.

<div align="center">

| Node | Grants | Vanilla fallback |
|:---|:---|:---:|
| `goidainvrestore.use` | Browse backups | op 2 |
| `goidainvrestore.restore` | Apply a restore | op 2 |
| `goidainvrestore.manage` | Manual backup, delete, clear | op 2 |

</div>

<br>

## 💾 Storage backends

<table align="center">
<tr><th>File <sub>(default)</sub></th><th>MySQL</th></tr>
<tr>
<td>

Zero setup — per-player `.dat` files under the world folder. Best for a single server.

</td>
<td>

Driver bundled in the jar with its own isolated class loader — no classpath clashes with other mods shipping Connector/J. Best for shared backups across a network.

</td>
</tr>
</table>

Writes happen off the main thread by default (`backup.asyncWrites`), so taking a snapshot never costs a tick stall, even with a large history.

<br>

## 🧩 Compatibility

| Mod | Integration |
|:---|:---|
| [Curios](https://www.curseforge.com/minecraft/mc-mods/curios) | Curio slots included in every snapshot and restore |
| Cosmetic Armor Reworked | Cosmetic layer included in every snapshot and restore |
| Sophisticated Backpacks | Equipped backpack contents included |
| LuckPerms | Permission nodes resolved through NeoForge `PermissionAPI` |
| FTB Ranks | Resolved directly when LuckPerms-style nodes aren't present |
| Bukkit-hybrid cores <sub>(Mohist, etc.)</sub> | Falls back to the Bukkit permission system |

All three item-storage integrations are reflection-based: the mod compiles and runs identically whether or not Curios, Cosmetic Armor Reworked, or Sophisticated Backpacks are installed.

<br>

## 📋 Requirements

<div align="center">

| | |
|:---|:---:|
| **Minecraft** | `1.21.1` |
| **NeoForge** | `21.1.233+` |
| **Java** | `21` |
| **Side** | Server only |

</div>

<br>

## 📥 Installation

1. Download the latest jar from [**Releases**](https://github.com/Yukovsky/GoidaInvRestore/releases).
2. Drop it in the `mods/` folder of your NeoForge server.
3. Start the server — the config is generated at `config/goidainvrestore-server.toml`.

<br>

## ⚙️ Configuration

<details>
<summary><code>config/goidainvrestore-server.toml</code> — click to expand</summary>

```toml
[backup]
  periodicIntervalMinutes = 30   # 0 disables periodic backups
  maxRecordsPerPlayer = 20       # ring buffer size, oldest dropped first
  onDeath = true
  onLogout = false
  dedupPeriodic = true           # skip a periodic backup if nothing changed
  preRestoreBackup = true        # snapshot current state before every restore
  asyncWrites = true             # write off the main thread

[capture]
  enderChest = true
  curiosAndCosmetic = true       # only relevant if those mods are installed
  experience = true

[permissions]
  adminLevel = 2                 # op level fallback when no permission mod is present

[storage]
  backend = "file"                # "file" or "mysql"

  [storage.mysql]
    host = "localhost"
    port = 3306
    database = "goidainvrestore"
    user = "root"
    password = ""
    tablePrefix = "goidainvrestore_"
    useSsl = false
```

</details>

<br>

## 🔨 Building from Source

```bash
git clone https://github.com/Yukovsky/GoidaInvRestore.git
cd GoidaInvRestore
./gradlew build
```

Output jar: `build/libs/goidainvrestore-<version>.jar`. Requires Java 21.

<br>

## 📄 License

<div align="center">

Released under the [**Apache License 2.0**](LICENSE).

</div>
