![SurvivalGames](https://raw.githubusercontent.com/itsthechris07/SurvivalGames/main/branding/banner.png)

# SurvivalGames

Survival Games and Varo for Paper 1.21.11 – 26.3 (and Purpur) – several games next to each other on one server, or
one game per server in a network behind Velocity/BungeeCord.

- **Survival Games** (the default): everybody spawns on a spawn point, loots the chests and fights; after a grace
  period and a deathmatch with a shrinking world border the last one standing wins. Every round starts with a fresh
  copy of the map.
- **Varo**: teams play in the same world for days, every player may only play a few minutes per day and is out after
  the first death – the last team wins. The world and the game are kept until an admin resets it.

![Two games, one plugin](https://raw.githubusercontent.com/itsthechris07/SurvivalGames/main/branding/how-it-works.png)

## Features

![Features](https://raw.githubusercontent.com/itsthechris07/SurvivalGames/main/branding/features.png)

![Gameplay](https://raw.githubusercontent.com/itsthechris07/SurvivalGames/main/branding/gameplay.png)

- Arenas with their own map (a folder in `maps/`, an imported world or a generated one), set up in a copy of the map
  (`/sg arena <arena> edit`), spawn points or a spawn circle, lobby, center and world border
- Server modes: `standalone` (games run "on the side", players join with `/sg join` or a `[sg]` sign), `game` (one
  game per server, players come from the lobby server) and `lobby` (sends players to free game servers, reads their
  state from the MOTD)
- Survival Games: map voting, kits, lobby countdown, frozen start, loot tables per container with refills and upgrades
  (`loot.yml`), grace period, shrinking world border, player tracker, supply drops, lightnings that reveal the
  players, deathmatch, spectators who sponsor items, only some blocks breakable
- Varo: play time per day, no logout during a fight (a villager stands in for players who leave), join protection,
  shrinking world border, strikes, peaceful days, inactive players are out, daily coordinates, banned items, potions
  and enchantments, daily backups with a restore command, the players get their own inventory back when they leave the game
- Statistics (games, wins, kills, deaths, Elo rating) per game type in a file or a MySQL database: `/sg stats`,
  `/sg top`, leaderboard holograms and `[sgtop]` signs
- Achievements (first win, 3 kills in a game, winning without armor, a Varo without a strike, ...): `/sg achievements`
- Update checker: admins read about new releases when they join
- Replays: every round of Survival Games and every day of a Varo is recorded and can be watched again in a copy of the
  map (`/sg replay`) – pause, speed, jumps and the view of every player, without a client mod
- Rewards (money and console commands), bounties, fireworks, the inventory of the killer after a death
- Menus instead of commands: a main menu for the players (`/sg`), all games (`/sg games`), and for admins the setup
  of the arenas, the control of the games and the settings (`/sg admin`, `/sg settings`)
- Teams (menu or `/sg team create <name> [color]`), team chat (`@message`), separate chat per game, scoreboard
- Extras (per game type): backpack, chest lock with a `[Lock]` sign, gravity grenade, toxic rain, tracker, supply drops,
  kits, bounties, sponsors, item restrictions
- English and German messages (MiniMessage, clickable commands), PlaceholderAPI (`%sg_...%`) and MiniPlaceholders
- Integrations: Vault/VaultUnlocked, Parties, Party and Friends, Citizens and FancyNpcs (join NPCs), Floodgate
  (Bedrock forms), TAB, EssentialsX, vanish plugins, Multiverse

## Quick start
1. Put the jar into `plugins/` and start the server.
2. Put a map into `plugins/SurvivalGames/maps/<name>` (or import a world: `/sg arena create <name> --world <world>`,
   or generate one: `--generate`) and create the arena: `/sg arena create <name>` (`--type varo` for Varo).
3. `/sg arena <name> edit`, set `center` and `spawn add`, then `/sg arena save`. The lobby of all arenas:
   `/sg lobby setworld <world>` (or one per arena in its map: `/sg arena <name> lobby`).
4. `/sg arena <name> creategame` – players join with `/sg join` or a sign with `[sg]` and the arena name.

All commands: `/sg help` (alias `/varo`). Admin permission: `sg.admin` (operators).

Full documentation: [itsthechris07.github.io/SurvivalGames](https://itsthechris07.github.io/SurvivalGames/) (source in
[`docs/`](docs/index.md)).

## Building
Requires JDK 25.

```
./gradlew build
```

The plugin jar is `build/libs/SurvivalGames-<version>.jar`.
