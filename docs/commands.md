# Commands and permissions

All commands start with `/sg` – `/survivalgames` and `/varo` work too. `/sg help [query]` lists them in the game
(click to run). `<...>` is needed, `[...]` is optional.

## Players

| Command | |
|---|---|
| `/sg` | the plugin version |
| `/sg join [arena]` | joins a game (without: the fullest free game; on a lobby server: an arena is a server) |
| `/sg spectate [arena]` | watches a running game |
| `/sg leave` | leaves your game |
| `/sg list` | all games (on a lobby server: all game servers) |
| `/sg info [arena]` | the state and the rules of a game |
| `/sg time` | how long you can still play today (Varo) |
| `/sg team` | the team menu |
| `/sg team list` | the teams of your game and their players |
| `/sg team create <name> [color]` | creates a team and joins it (`teams.players-can-create`) |
| `/sg team join <team>` | joins a team |
| `/sg team leave` | leaves your team |
| `/sg ping [player]` | the ping of a player |
| `/sg lastseen <player>` | when a player was online the last time |
| `/sg stats [player]` | the statistics of a player per game type (games, wins, kills, deaths, K/D) |
| `/sg top [stat] [type]` | the best 10 players (stat: `games`, `wins`, `kills`, `deaths`, `rating`; default: wins) |
| `/sg strikes` | your strikes in your Varo |
| `/sg vote [arena]` | votes for the map in the lobby of Survival Games (without: the menu, see [map voting](survival-games.md#map-voting)) |
| `/sg kit [kit]` | chooses a [kit](survival-games.md#kits) in the lobby (without: the menu) |
| `/sg bounty [player] [amount]` | puts a [bounty](survival-games.md#bounties) on a player (without an amount: the bounty on them, without a player: all bounties) |
| `/sg sponsor [player]` | spectators send [items](survival-games.md#sponsors) to a player alive (without: the menu) |
| `/sg inventory <id>` | opens the [inventory](survival-games.md#the-end) of a message after a kill or a game (click the message) |

## Admins (`sg.admin`)

### Arenas

| Command | |
|---|---|
| `/sg arena create <name> [--type <type>] [--map <map>] [--world <world>] [--generate] [--seed <seed>]` | creates an arena (see [Getting started](getting-started.md#1-the-map)) |
| `/sg arena list` | all arenas |
| `/sg arena <arena> info` | the setup of an arena |
| `/sg arena <arena> edit` | teleports you into a copy of the map |
| `/sg arena save` | saves the map you edit and teleports you back |
| `/sg arena <arena> lobby` / `center` | sets the lobby / the center to your position |
| `/sg arena <arena> spawn add` | adds a spawn point at your position |
| `/sg arena <arena> spawn list` / `tp <number>` / `remove <number>` | the spawn points |
| `/sg arena <arena> spawn clear --confirm` | deletes all spawn points |
| `/sg arena <arena> players <min> <max>` | the number of players |
| `/sg arena <arena> teamsize <size>` | players per team |
| `/sg arena <arena> border <size>` | the world border (0: none) |
| `/sg arena <arena> type <type>` | `survival-games` or `varo` |
| `/sg arena <arena> displayname <name>` | the name shown to the players (`reset`: the arena name) |
| `/sg arena <arena> creategame` | creates the game of the arena, also after every restart |
| `/sg arena <arena> removegame` | stops the game and creates no new one |
| `/sg arena <arena> delete --confirm` | deletes the arena (the map stays, a saved Varo and its world are deleted) |

### Games

| Command | |
|---|---|
| `/sg start [--force]` | starts your game right away (`--force`: with fewer players) |
| `/sg game <arena> start [--force]` | starts the game of an arena |
| `/sg game <arena> stop` | stops the game (a Varo is saved) |
| `/sg game <arena> create` | creates the game once (not after a restart) |
| `/sg game <arena> deathmatch` | starts the deathmatch right away (Survival Games) |
| `/sg game <arena> reset [--teams] --confirm` | starts a Varo again from the lobby |
| `/sg game <arena> revive <player>` | brings a player who is out back into the game |
| `/sg game <arena> strike <player> [reason]` | gives a player a strike (Varo, see [strikes](varo.md#strikes)) |
| `/sg game <arena> unstrike <player>` | takes back the last strike of a player |
| `/sg game <arena> strikes [player]` | everybody with strikes / the strikes of a player |
| `/sg game <arena> teams` | the teams of the game |
| `/sg game <arena> team create <name> [color]` / `delete <team>` | creates / deletes a team |
| `/sg game <arena> team add <team> <player>` / `remove <player>` | puts a player into a team / takes them out |

### Kits, leaderboards and NPCs

| Command | |
|---|---|
| `/sg kit create <name> [--permission]` | saves your inventory as [kit](survival-games.md#kits), the item in your hand is its icon (`--permission`: only for players with `sg.kit.<name>`) |
| `/sg kit edit <kit>` | puts the kit into your empty inventory – change it and save it again with `create` |
| `/sg kit delete <kit>` / `list` | deletes a kit / lists the kits |
| `/sg leaderboard create <stat> [type]` | a hologram with the top 10 of a statistic at your position (see [leaderboards](configuration.md#leaderboards)) |
| `/sg leaderboard remove` / `list` | removes the nearest hologram (5 blocks) / lists holograms and signs |
| `/sg npc link <arena>` | the NPC you click next becomes a join NPC (see [Integrations](integrations.md#join-npcs)) |
| `/sg npc unlink` / `list` | removes the link of the NPC you click next / lists the join NPCs |

### Other

| Command | |
|---|---|
| `/sg settings [type] [setting] [value]` | changes a switch or number of a game type in `config.yml` (without a value: a [menu](configuration.md#changing-settings-in-the-game)) |
| `/sg build` | build mode: change the world of your game (e.g. in the lobby) |
| `/sg reload` | reloads `config.yml`, `loot.yml` and the messages |
| `/sg debug` | debug information |

## Permissions

| Permission | Default | |
|---|---|---|
| `sg.admin` | operators | all admin commands, join signs, leaderboard signs, join NPCs |
| `sg.network.bypass` | nobody | game servers of a network let the player in although they can't play (operators always get in) |
| `sg.kit.<kit>` | nobody | the kit, if it was created with `--permission` |
| `sg.vote.2` ... `sg.vote.5` | nobody | the vote of the player for a map counts 2 ... 5 times |
