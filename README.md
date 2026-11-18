<p align="center"><img src="DiscordLink_ikon_128.png" width="128"></p>
<h1 align="center">DiscordLink</h1>
<p align="center">Link Minecraft and Discord, sync ranks and roles, and log every link.<br>Made by <b>Mineconed</b> • Minecraft 1.8 to 1.21</p>

## Features
- 🔗 **/link** in Minecraft gives a code. Click to copy (1.15+)
- 🖱️ **Link panel** in Discord with a button and a popup to type the code
- 💬 Slash commands: **/link, /unlink, /account, /whois, /linkpanel**
- 🎭 **Rank sync**: Minecraft groups give Discord roles
- 🔄 **Role sync**: Discord roles give Minecraft groups (fx boosters)
- 📋 **Link logs** in a Discord channel: Minecraft name, UUID, Discord user, Discord ID and when the Discord account was made
- ⚠️ **Alt warning** for new Discord accounts
- 📁 Every link is also saved in **link_log.txt** and shown with **/dl history**
- ✅ Linked role and nickname sync
- 🔒 Optional: players **must link to join**
- 🛠️ Staff can force link, force unlink and look up anyone
- 🌐 English and Danish built in

## Setup
1. Put **DiscordLink.jar** in your plugins folder and start the server
2. Make a bot on discord.com/developers, copy the token and invite it with **Manage Roles** and **Manage Nicknames**
3. Put the token and your server id in **config.yml** and restart
4. Put the bot role **above** the roles it gives
5. Set **logs.channel_id** and send the panel with **/dl panel <channel id>**

## Commands
| Command | What it does |
|---|---|
| `/link` | get a link code |
| `/unlink` | unlink your Discord |
| `/dl info <player>` | see who a player is linked to |
| `/dl history [player]` | the last links |
| `/dl forcelink <player> <discord id>` | link a player |
| `/dl forceunlink <player>` | unlink a player |
| `/dl sync` | sync all online players now |
| `/dl panel [channel id]` | send the link panel |
| `/dl stats` | number of links and bot status |
| `/dl reload` | reload |

## Permissions
| Permission | What it does |
|---|---|
| `discordlink.link` | use /link and /unlink (everyone) |
| `discordlink.admin` | the /dl commands (op) |

## Config
| Setting | What it does |
|---|---|
| `bot.token / bot.guild_id` | your bot token and Discord server id |
| `link.code_minutes` | how long a code works |
| `link.max_accounts_per_discord` | Minecraft accounts per Discord |
| `link.linked_role` | role for every linked player |
| `link.sync_nickname` | Discord nickname = Minecraft name |
| `link.allow_unlink` | players can unlink themselves |
| `link.unlink_on_leave` | unlink players who leave the Discord |
| `link.require_link` | must link to join |
| `logs.channel_id` | channel for link logs |
| `logs.file` | write link_log.txt |
| `logs.new_account_warning_days` | alt warning |
| `sync.minecraft_to_discord` | group=ROLE_ID |
| `sync.discord_to_minecraft` | ROLE_ID=group |
| `sync.add_command / remove_command` | commands used for groups |
| `whois.admin_only` | only staff can use /whois |

## Versions
1.8 to 1.21 (Spigot and Paper). Java 8 or newer.

## Build
You need Java 8 or newer and Maven.
```
mvn package
```
The plugin is in `target/DiscordLink.jar`.

## License
MIT. Made by Mineconed.
