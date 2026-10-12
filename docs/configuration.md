# Configuration

Everything is in `plugins/SurvivalGames/`. After a change run `/sg reload`. New options of an update are added to your
files automatically – your values and comments stay.

## Files of the plugin

| File | Content |
|---|---|
| `config.yml` | the settings below |
| `loot.yml` | the [loot](#loot) of the chests in Survival Games |
| `messages.yml`, `messages_de.yml` | the [messages](customization.md) |
| `arenas.yml` | the arenas: game type, map, lobby, center, spawn points, players |
| `lobby.yml` | the [lobby world](getting-started.md#the-lobby-world): its map and spawn |
| `maps/` | the maps of the arenas and the lobby world (world folders) |
| `games/<arena>.yml` | a running Varo: state, teams, players, play time, their inventories in the game, backpacks, bounties |
| `signs.yml` | the join signs |
| `stats.yml` | the [statistics](#statistics) (with `stats.storage: file`) |
| `kits.yml` | the [kits](survival-games.md#kits) |
| `leaderboards.yml` | the [leaderboards](#leaderboards): holograms and signs |
| `npcs.yml` | the [join NPCs](integrations.md#join-npcs) |
| `backups/<uuid>.yml` | the inventory, location, ... of players outside of the games while they play (restored after a crash) |
| `backups/<arena>/<day>.zip`, `<day>_<time>.zip` | the [backups](varo.md#backups) of a Varo |
| `replays/<id>.sgr` | the recordings of the [replays](replays.md) |

!!! warning
    Don't edit `arenas.yml` and `games/` while the server is running – use the commands.

## Most important options

| Option | Default | Meaning |
|---|---|---|
| `language` | `en` | `en` or `de` (or your own, see [messages](customization.md)) |
| `network.mode` | `standalone` | `standalone`, `game` or `lobby` (see [Network](network.md)) |
| `chat.separate-games` | `true` | players only see the chat of their own game |
| `survival-games.min-players` | `2` | players needed for the lobby countdown |
| `survival-games.grace-period` | `60` | seconds without PvP after the start |
| `survival-games.deathmatch.minutes` | `20` | when the deathmatch starts |
| `survival-games.world-border.shrink-after` | `8` | minutes until the border starts shrinking (0: only in the deathmatch) |
| `survival-games.map-voting.enabled` | `true` | the players [vote for the map](survival-games.md#map-voting) in the lobby |
| `survival-games.map-voting.block-last` | `true` | the map of the last round can't be voted for again right away |
| `survival-games.reveal.minutes` | `12` | when [lightnings](survival-games.md#lightnings) start to show where the players are |
| `<game type>.advancements` | `false` | players make progress in their advancements while they are in a game |
| `<game type>.fireworks` | `true` | fireworks for the winners |
| `varo.play-time` | `15` | minutes every player may play per day |
| `varo.day-start-hour` | `0` | the hour (0-23) a new day starts |
| `varo.play-hours` | `[ ]` | the [times of the day](varo.md#playing-every-day) a Varo can be played at, e.g. `[ "18:00-20:00" ]` (empty: always) |
| `varo.team-size` | `2` | players per team |
| `varo.strikes` | `[ coordinates, inventory, out ]` | the consequences of the 1st, 2nd, ... [strike](varo.md#strikes) |
| `varo.peaceful-days`, `inactive-days`, `coordinates-from-day` | `0` | the [optional rules](varo.md#optional-rules) of a Varo |
| `varo.offline-npc-seconds` | `60` | a villager stands in for players who leave the server |
| `varo.backups` | `7` | the number of [backups](varo.md#backups) of a Varo that are kept |
| `stats.storage` | `file` | `file` (`stats.yml`) or `mysql` (see [statistics](#statistics)) |
| `stats.rating.games` | `[ survival-games ]` | the game types with an [Elo rating](#rating) |
| `stats.achievements.enabled` | `true` | the players get [achievements](#achievements) |
| `update-checker.enabled` | `true` | looks for new releases on GitHub when the server starts and every 12 hours – admins (`sg.admin`) read it when they join |
| `rewards.enabled` | `false` | [money](#rewards) for kills, wins and games |
| `replays.enabled` | `true` | the games are recorded and can be watched again (see [replays](replays.md)) |
| `replays.games` | both | the game types that are recorded |
| `replays.keep` | `10` | the newest recordings that are kept per arena (`0`: all of them) |
| `replays.max-playbacks` | `2` | the replays that can be watched at the same time (every one has its own world) |
| `modules.<module>.games` | | the game types an extra is used in |

Arenas override `min-players`, `max-players`, `team-size` and `world-border.size` of their game type
(`/sg arena <arena> players|teamsize|border`).

## Changing settings in the game

`/sg settings` opens a menu with the switches and numbers of `survival-games` and `varo` in `config.yml`: a click
turns a switch on or off, numbers are typed (`/sg settings <type> <setting> <value>`, Bedrock players get a form).
The same as a command:

```
/sg settings varo peaceful-days 2
/sg settings survival-games map-voting.enabled off
/sg settings varo world-border.damage 0.5
```

The change is saved in `config.yml` and works right away. Lists (`strikes`, `breakable-blocks`, ...) and everything
outside of the two game types are changed in the file.

## Statistics

Every game counts the games, wins, kills and deaths of the players per game type (`/sg stats`, `/sg top`, the
[placeholders](placeholders.md#statistics)). They are saved in `stats.yml` – or in a MySQL/MariaDB database, so that
all servers of a [network](network.md) share them:

```yaml
stats:
  enabled: true
  storage: mysql
  mysql:
    url: jdbc:mysql://localhost:3306/minecraft
    user: minecraft
    password: secret
    table: sg_stats
```

The table is created automatically. Paper ships the MySQL driver, nothing else has to be installed.

### Rating

In the game types of `stats.rating.games` every player has an **Elo rating** – everybody starts with 1000. At the end
of a game every player is compared with every player of the other teams: who lasted longer has beaten the other one.
Winning against players with a higher rating brings more than against weaker ones; one game changes the rating by
`stats.rating.k-factor` at most, no matter how many players took part. `/sg top rating` lists the best, the players
see their change at the end of the game.

### Leaderboards

The best players in the world, without other plugins – updated every minute:

- **Holograms:** `/sg leaderboard create <stat> [type]` puts the top 10 of a statistic (`wins`, `kills`, `games`,
  `deaths`, `rating`; of one game type or all) where you stand. `/sg leaderboard remove` removes the nearest one.
- **Signs:** a sign with `[sgtop]` in the first line, the statistic in the second, the rank (1-10, default 1) in the
  third and – if wanted – the game type in the fourth shows the player on that rank. A **player head** above the sign
  (or above the block a wall sign hangs on) gets their skin. Only admins can create the signs; sneak to break them.

`/sg leaderboard list` lists all of them. For holograms of other plugins use the [placeholders](placeholders.md).

### Achievements

With `stats.achievements.enabled` the players collect achievements. They are kept with the statistics (`stats.yml` or
the table `<table>_achievements` of the database), so there are none with `stats.enabled: false`. A new achievement
is announced in the chat, `/sg achievements` shows the own ones in a menu, `/sg achievements <player>` lists those of
another player. The names and descriptions are texts of `messages.yml` (`achievement_...`).

| Achievement | How to get it |
|---|---|
| Tribute, Veteran | play 1 / 50 games |
| First strike, Hunter | kill 1 / 100 players |
| Victor, Champion, Legend | win 1 / 10 / 50 games |
| First blood | the first kill of a game |
| Rampage, Unstoppable | 3 / 5 kills in one game |
| Bare skin | win without wearing armor |
| Pacifist | win without a kill |
| Last breath | win with one heart or less |
| Bounty hunter | collect the bounty on a player |
| Sponsor | send an item to a player as a spectator |
| Survivor | win a Varo alive |
| Spotless | win a Varo without a strike |

Games, kills and wins count over all game types. What a player reached while they were offline (their team won a
Varo) comes with their next join.

## Rewards

With `rewards.enabled` the players get money of an economy plugin ([Vault](integrations.md#economy)) – `kill`, `win`
(every player of the winning team) and `game` (everybody who took part), `0` = nothing.

`rewards.commands` runs commands of the console for the same events, also without an economy plugin and with
`enabled: false`:

```yaml
rewards:
  commands:
    kill: [ ]
    win:
      - "give {player} diamond 1"
      - "[10s] broadcast {player} has won in {arena}!"   # 10 seconds later
    game: [ ]
```

`{player}`, `{uuid}`, `{arena}` and `{type}` are replaced.

## The whole file

```yaml
--8<-- "src/main/resources/config.yml"
```

## Loot

`loot.yml` decides what is in the chests, trapped chests and barrels of Survival Games. Every container gets
`items.min` to `items.max` random items when it is opened for the first time and again after every refill.

- **Tables:** `normal`, `center` and `supply` are lists of items – add as many lists of your own as you like.
  `item` is written like in `/give` (also with components: `"potion[potion_contents={potion:\"healing\"}]"`),
  `amount` is a number or a range (`1-3`), `weight` says how often the item is chosen compared to the others.
- **`containers`** gives every kind of container its table, e.g. `barrel: food`.
- **`center-radius`:** containers this close to the center of the arena get the `center` loot instead of the `normal`
  loot.
- **`upgrades`** makes the loot better after refills: `2: { normal: center }` – from the 2nd refill on the containers
  with normal loot get the center loot.
- [Supply drops](survival-games.md#supply-drops) get `supply-items.min` to `supply-items.max` items of the `supply`
  list.

When the plugin is updated, new options are added to your `loot.yml` – but entries you delete from `containers` and
`upgrades` stay deleted.

```yaml
--8<-- "src/main/resources/loot.yml"
```
