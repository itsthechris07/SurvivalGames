# Survival Games

The default game type. The settings are in `survival-games` in [`config.yml`](configuration.md); arenas override
the number of players, the team size and the world border.

## Lobby

The players wait in the lobby of the arena: nobody can break blocks, take damage or get hungry. With teams
(`team-size` > 1) they choose a team with the team item or `/sg team`; who has no team at the start gets one.

Once `min-players` are there, the **lobby countdown** (`lobby-countdown`) starts. When the game is full it is
shortened to `full-countdown`. If too many players leave, it stops.

## Start

Everybody is put onto a spawn point (or into the spawn circle) with an empty inventory and stands still until the
**start countdown** (`start-countdown`) is over. Then the round begins.

- **Grace period:** for `grace-period` seconds nobody can hurt the others.
- **Loot:** chests, trapped chests and barrels are filled when they are opened for the first time and again every
  `refill-minutes`. Containers near the center (`center-radius` in `loot.yml`) get better loot. See
  [loot.yml](configuration.md#loot).
- **Blocks:** only the `breakable-blocks` can be broken and only the `placeable-blocks` placed (blocks placed in the
  game can be broken again). `building: true` allows everything.
- The time of day is fixed (`time`), monsters don't spawn (`monsters`).

## Deathmatch

After `deathmatch.minutes` or once only `deathmatch.players` are left, everybody is warned (`deathmatch.warning`
seconds) and then teleported back onto the spawn points. The world border around the center shrinks from
`deathmatch.border-size` to `deathmatch.final-border-size` within `deathmatch.border-minutes`.
`/sg game <arena> deathmatch` starts it right away.

## The end

Who dies is out and watches the rest (`spectators: true`) or leaves the game. The last player (or team) standing
wins. After `ending-seconds` everybody leaves the arena and the next round starts with a fresh copy of the map.

Spectators fly around invisibly, can't touch anything and can't be seen by the players. `/sg spectate` lets everybody
else watch a running game.
