# SurvivalGames – notes for AI assistants

<!-- Written by the SurvivalGames plugin on every start and replaced by updates - don't edit it, your changes would
be lost. Set agent-files: false in config.yml to stop writing it. -->

This folder (`plugins/SurvivalGames/`) belongs to the Minecraft plugin **SurvivalGames** (Paper/Purpur): Survival
Games, Varo, UHC and Duels. If you help the owner of the server to configure it, follow these notes.

Documentation: https://itsthechris07.github.io/SurvivalGames/ (configuration:
https://itsthechris07.github.io/SurvivalGames/configuration/, commands and permissions:
https://itsthechris07.github.io/SurvivalGames/commands/).

## Rules

- **Never invent options.** Every option that exists is in `config.yml` (and `loot.yml`), each with a comment that
  explains it and its values. Unknown keys are ignored without an error, so a made-up key silently does nothing. If
  the owner wants something that has no option, say so instead of guessing one.
- Keep the structure, the indentation (2 spaces) and the comments of the files. Change values, don't rewrite files.
- After a change of `config.yml`, `loot.yml` or a messages file the owner runs `/sg reload` (or restarts the server).
- **Don't edit while the server is running:** `arenas.yml`, `lobby.yml`, `signs.yml`, `stats.yml`, `kits.yml`,
  `leaderboards.yml`, `npcs.yml`, `games/`, `backups/`. The plugin keeps them in memory and overwrites them. Arenas,
  spawn points, the lobby, kits, signs, ... are set up in the game with commands (`/sg arena ...`, `/sg admin`).
- Never touch `maps/` (world folders), `replays/` (binary recordings) or the `.zip` backups of a Varo.
- Don't set `network.mode` to `game` or `lobby` unless the owner really runs a network of several servers (BungeeCord
  or Velocity) - `game` makes every player join the one game of the server.
- Passwords (`stats.mysql`) and webhook URLs (`discord.webhook-url`) are secrets: don't repeat them or put them
  anywhere else. Values of `config.yml` can refer to environment variables (`password: "${SG_MYSQL_PASSWORD}"`) -
  suggest that instead of writing a secret into the file, and never resolve or print such a variable.

## Files

| File | What it is | Edit? |
|---|---|---|
| `config.yml` | all settings: language, game types (`survival-games`, `varo`, `uhc`, `duels`), `modules`, `stats`, `rewards`, `replays`, `network`, ... | yes |
| `loot.yml` | the loot of the chests in Survival Games (tables per container, upgrades) | yes |
| `messages.yml`, `messages_<language>.yml` | all texts (MiniMessage) of the language set with `language` | yes |
| `messages-outdated.yml` | old default texts the plugin replaces on updates | no |
| `arenas.yml`, `lobby.yml`, `signs.yml`, `kits.yml`, `leaderboards.yml`, `npcs.yml` | the setup made in the game | not while the server runs |
| `stats.yml`, `games/`, `backups/`, `replays/`, `maps/` | data of the plugin | no |

## config.yml

- Every game type has its own section with the same kind of settings (players, countdowns, grace period, world
  border, ...). A setting of `survival-games` doesn't change `uhc` and the other way round.
- `modules.<module>.enabled` switches a module, `modules.<module>.games` lists the game types it works in
  (`survival-games`, `varo`, `uhc`, `duels`).
- Times are in seconds unless the key or its comment says minutes (`refill-minutes`, `minutes`, ...).
- Lists of materials or mobs use the Minecraft names in lower case (`oak_log`, `iron_sword`, `silverfish`).
- Most switches and numbers of the game types can also be changed in the game: `/sg settings <type> <setting>
  <value>` (saved to `config.yml`, no reload needed). If the server is running this is safer than editing the file.

## Messages

- The texts use MiniMessage: `<red>`, `<bold>`, `<#ff8800>`, `<gradient:gold:red>`. `{0}`, `{1}`, ... are filled in by
  the plugin - keep every placeholder of a text, also when you translate it.
- Commands in texts are clickable: `<run:"/sg leave">...</run>`, `<suggest:"/sg team create ">...</suggest>` - keep
  them.
- A new language: copy `messages.yml` to `messages_<language>.yml` (e.g. `messages_fr.yml`), translate the values (not
  the keys) and set `language: fr`. Missing texts are English.

## Checking a change

- `/sg reload` reloads the files, errors in the YAML are shown in the console.
- `/sg debug` shows the version, the mode, the arenas and the games.
- `/sg arena <arena> info` shows what an arena still needs.
