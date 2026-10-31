# UHC

UHC (Ultra Hardcore) is a round in a **newly generated world**: there is no loot, everything is mined and crafted.
Health doesn't come back by itself, the teams start at random places and the shrinking world border pushes everybody
into the center. The last one standing wins, then the next round starts in a new world. The settings are in `uhc` in
[`config.yml`](configuration.md).

## Setup

```
/sg arena create uhc1 --type uhc
```

The arena gets a generated world (a new one every round, `--seed <seed>` for always the same one). With
`--map <map>` or `--world <world>` UHC is played on a map instead. No spawn points are needed – only a lobby (the
[lobby world](getting-started.md#the-lobby-world) or `/sg arena uhc1 lobby`). The center of the world border is the spawn of
the world unless the arena has its own center.

The lobby, countdowns, join signs, NPCs, teams, spectators, statistics and replays work like in
[Survival Games](survival-games.md).

## A round

- **Generation:** while the players wait in the lobby, the world inside the border is generated, the center first
  (`pregenerate`). A round can start before it is done – the rest is generated when somebody gets there.
- **Scatter:** every team starts at a random place inside the world border, at least `scatter-distance` blocks away
  from the other teams (less if there is no room), never in water or lava. The members of a team start together.
- **Grace period:** no PvP for `grace-period` seconds (10 minutes by default).
- **No natural regeneration:** health only comes back with golden apples, golden heads, potions and the final heal
  (`natural-regeneration: false`). The hearts of every player are shown in the tab list (`health-in-tab`).
- **Golden heads:** a player who dies drops their head. Eight gold ingots around it make a golden head – like a golden
  apple, but the regeneration lasts longer (`golden-heads`).
- **Final heal:** `final-heal` minutes after the start everybody alive gets full health and food (announced in the
  last minute).
- **World border:** `world-border.size` blocks (1000) at the start, it shrinks after `shrink-after` minutes to
  `final-size` blocks in `shrink-minutes` minutes.
- **Deathmatch** (off by default): after `deathmatch.minutes` or once only `deathmatch.players` are left, everybody
  alive is teleported into a circle around the center.
- **No portals:** nether and end portals don't work in UHC.

## Scenarios

`scenarios` in `uhc` turns on extra rules for every round:

| Scenario   | What it does                                                                     |
|------------|----------------------------------------------------------------------------------|
| `cutclean` | iron, gold and copper ores drop ingots, animals drop cooked meat                 |
| `timber`   | breaking a log fells the whole tree (the logs connected to it, at most 256)      |

```yaml
uhc:
  scenarios: [ cutclean, timber ]
```

## Extras

The [extras](configuration.md) (`modules`) are used in UHC when `uhc` is in their `games` list – by default the
inventory of the killer (`inventory-view`) and bounties. Kits, the tracker or supply drops can be added the same way.
