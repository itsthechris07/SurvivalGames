# Getting started

## Installation

1. Download `SurvivalGames-<version>.jar` and put it into the `plugins` folder of your server.
2. Start the server. The plugin creates `plugins/SurvivalGames/` with `config.yml`, `loot.yml`, the messages and the
   folder `maps/`.
3. Choose how the server runs (`network.mode` in [`config.yml`](configuration.md)):
    - `standalone` (default): the games run on this server next to everything else. Players who aren't in a game
      aren't affected at all.
    - `game` / `lobby`: one game per server in a network – see [Network](network.md).

All setup commands need the permission `sg.admin` (operators have it).

## 1. The map

Every arena plays on a map – a world folder that stays untouched; each game plays in a copy of it.

- **Your own map:** put the world folder into `plugins/SurvivalGames/maps/<name>` and run `/sg arena create <name>`.
- **A world of the server:** `/sg arena create <name> --world <world>` copies it into `maps/`.
- **Another arena's map:** `/sg arena create <name> --map <map>` – several arenas can share one map.
- **A generated world:** `/sg arena create <name> --generate [--seed <seed>]` – a new random world for every game
  (good for Varo).

The game type is Survival Games unless you add `--type varo`. You can change it later with `/sg arena <name> type`.

## 2. Setting up the arena

`/sg arena <name> edit` teleports you into a copy of the map (creative mode, your inventory is kept for later). Then
stand at the right place and run:

| Command | Sets |
|---|---|
| `/sg arena <name> lobby` | where the players wait before the game (default: the [lobby world](#the-lobby-world), without one the center) |
| `/sg arena <name> center` | the middle of the world border, the spawn circle and the deathmatch (default: the world spawn) |
| `/sg arena <name> spawn add` | a spawn point (the middle of the block you stand on; the players look at the center) – once per player |

`/sg arena save` saves the copy back into the map and teleports you back. Without spawn points the players spawn in a
circle around the center (`spawn-circle-radius`).

More settings: `/sg arena <name> players <min> <max>`, `teamsize <size>`, `border <size>`, `displayname <name>`.
`/sg arena <name> info` shows the whole setup and what is still missing (click the hints).

!!! tip "Spawn points limit the players"
    With spawn points, at most as many players as there are spawn points can join.

### The lobby world

Usually a server has **one lobby** for all arenas: a world of its own the players wait in until their game starts.

1. `/sg lobby setworld <world>` – a world of the server (it is copied into `maps/`) or a map that already is in
   `maps/`.
2. `/sg lobby edit` teleports you into a copy of it, `/sg lobby setspawn` sets where the players arrive (default: the
   spawn of the world), `/sg lobby save` saves it.

All arenas without a lobby of their own use it from now on; the game itself is played in the map of the arena. An
arena that has its own lobby (`/sg arena <name> lobby`) keeps it – `/sg arena <name> lobby remove` switches it to the
lobby world. `/sg lobby remove` removes the lobby world again.

Every game gets its own copy of the lobby world, so the players of different games don't meet. The copy is
removed when the game begins. A change of the lobby world is used by the games nobody is waiting in right away, by
the others in their next round.

## 3. The game

`/sg arena <name> creategame` creates the game of the arena – also after every restart. Players join with:

- `/sg join [arena]` – without an arena they join the fullest game that is waiting
- a **sign** with `[sg]` in the first line and the arena in the second (only admins can create it). It shows the state
  and the players, a click joins the game (or watches it while it runs). Sneak to break it.
- `/sg spectate [arena]` to watch a running game
- a **join NPC** of Citizens or FancyNpcs: `/sg npc link <arena>` and a click on the NPC – see
  [Integrations](integrations.md#join-npcs)

A round of Survival Games starts by itself once enough players are there; admins can start it right away with
`/sg game <arena> start` (`--force`: with fewer players). A Varo starts with `/sg game <arena> start`.

`/sg leave` (or the leave item) leaves the game. `/sg arena <name> removegame` stops the game and creates no new one.

## Next

- [Survival Games](survival-games.md) and [Varo](varo.md): how the games work
- [Network](network.md): one game per server
