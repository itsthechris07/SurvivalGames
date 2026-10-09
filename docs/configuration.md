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
| `maps/` | the maps of the arenas (world folders) |
| `games/<arena>.yml` | a running Varo: state, teams, players, play time, their inventories in the game, backpacks |
| `signs.yml` | the join signs |
| `backups/` | the inventory, location, ... of players outside of the games while they play (restored after a crash) |

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
| `varo.play-time` | `15` | minutes every player may play per day |
| `varo.day-start-hour` | `0` | the hour (0-23) a new day starts |
| `varo.team-size` | `2` | players per team |
| `modules.<module>.games` | | the game types an extra is used in |

Arenas override `min-players`, `max-players`, `team-size` and `world-border.size` of their game type
(`/sg arena <arena> players|teamsize|border`).

## The whole file

```yaml
--8<-- "src/main/resources/config.yml"
```

## Loot

`loot.yml` decides what is in the chests, trapped chests and barrels of Survival Games. Every container gets
`items.min` to `items.max` random items; containers within `center-radius` of the center use the `center` list.

```yaml
--8<-- "src/main/resources/loot.yml"
```
