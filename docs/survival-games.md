# Survival Games

The default game type. The settings are in `survival-games` in [`config.yml`](configuration.md); arenas override
the number of players, the team size and the world border.

## Lobby

The players wait in the [lobby world](getting-started.md#the-lobby-world) or the lobby of the arena: nobody can break blocks, take damage or get hungry. With teams
(`team-size` > 1) they choose a team with the team item or `/sg team`; who has no team at the start gets one.

Once `min-players` are there, the **lobby countdown** (`lobby-countdown`) starts. When the game is full it is
shortened to `full-countdown`. If too many players leave, it stops. Players with the permission `sg.start` (operators)
start the game right away with the start item (green dye) once `min-players` are there.

Monsters don't wait for the players: when they are put onto their spawn points, the monsters of the world are
removed, and until the game runs none spawn (also with `monsters: true`).

### Map voting

With `map-voting.enabled` the players choose the map: the vote item (or `/sg vote [arena]`) lists the arena of the
game and up to `map-voting.maps` - 1 other arenas of Survival Games with the same team size that nobody is waiting in.
`map-voting.end-seconds` before the start the votes are counted and everybody moves into the arena that has won (a
draw: the arena of the game stays).

- Players with the permission `sg.vote.2` ... `sg.vote.5` have 2 ... 5 votes.
- The map of the last round can't be chosen again right away (`map-voting.block-last`), unless it is the only one.
- There is nothing to vote for with only one arena, and on a [game server](network.md) (one arena per server).

### Kits

Admins save their inventory as a kit: `/sg kit create <name>`. Armor and off hand are included, the item in the hand
is the icon in the menu. With `--permission` only players with `sg.kit.<name>` can choose it. `/sg kit edit <kit>`
puts a kit into your (empty) inventory to change it, `/sg kit delete <kit>` deletes it.

The players choose their kit in the lobby with the kit item or `/sg kit [kit]` and get it when the game begins.
`modules.kits.default` is the kit of everybody who didn't choose one. The kits are saved in `kits.yml`.

## Start

Everybody is put onto a spawn point (or into the spawn circle) with an empty inventory and stands still until the
**start countdown** (`start-countdown`) is over. Then the round begins.
With `start-platforms: explode` players can move, but whoever leaves their spawn point before the end of the
countdown is put back and warned (`start-platform-warnings` times; the first 3 seconds and 2 seconds after a warning
they are only held back). After that they blow up and are out (jumping
and looking around is fine).

- **Grace period:** for `grace-period` seconds nobody can hurt the others. The time left is shown above the hotbar.
- **Loot:** chests, trapped chests and barrels are filled when they are opened for the first time and again every
  `refill-minutes`. Every kind of container has its own loot table, containers near the center get better loot and
  the loot gets better after some refills. See [loot.yml](configuration.md#loot).
- **Blocks:** only the `breakable-blocks` can be broken and only the `placeable-blocks` placed (blocks placed in the
  game can be broken again). `building: true` allows everything.
- The time of day is fixed (`time`), monsters don't spawn (`monsters`).
- **Advancements** are paused in the game (`advancements: false`).

## World border

The arena's world border (`world-border.size` or `/sg arena <arena> border`) starts shrinking
`world-border.shrink-after` minutes after the start (everybody is warned a minute before) down to
`world-border.final-size` blocks within `world-border.shrink-minutes`. Outside of it players take
`world-border.damage` per block and second. With `shrink-after: 0` it only shrinks in the deathmatch.

## Tracker

When the grace period is over, everybody alive gets a **player tracker**: a compass that points at the nearest enemy.
Holding it shows the name and the distance in the action bar (`modules.tracker`).

## Supply drops

Every `modules.supply-drop.minutes` a supply drop is announced with its coordinates near the center. A column of
sparks marks the place, `warning` seconds later a chest with the `supply` loot of [loot.yml](configuration.md#loot)
lands there, worth a fight. Not in the deathmatch.

## Lightnings

Nobody can hide until the end: after `reveal.minutes`, or once only `reveal.players` are left, a lightning (without
damage) strikes where every player alive is, again every `reveal.interval` seconds. `interval: 0` turns it off; in
the deathmatch there are no lightnings.

## Bounties

With an economy plugin ([Vault](integrations.md#economy)) the players put money on a player alive:
`/sg bounty <player> <amount>` (at least `modules.bounty.min`, at most `max`). The money is taken right away and
everybody sees the bounty. **Whoever kills the player gets all of it.** If they die otherwise, win or the round is
stopped, everybody gets their money back. `/sg bounty` lists all bounties, `/sg bounty <player>` shows one.

No bounties on yourself, on team mates and on players who are out. In a [Varo](varo.md) the bounties are saved with
the game.

## Sponsors

Spectators, also the players who are out, send items to the players alive: `/sg sponsor` opens the menu (the
players, then the items), `/sg sponsor <player>` the items for one player. The items and their prices are in
`modules.sponsor.items`:

```yaml
items:
  - { item: cooked_beef, amount: 4, price: 10 }
  - { item: "splash_potion[potion_contents={potion:\"healing\"}]", price: 40 }
  - { item: bread, price: 0 }   # free
```

Items with a price need an economy plugin ([Vault](integrations.md#economy)). A spectator can send one item every
`modules.sponsor.cooldown` seconds.

## Bets

With `modules.betting` (off by default) and an economy plugin ([Vault](integrations.md#economy)) spectators (also
the players who are out) bet on the winner: `/sg bet` shows the odds of the teams alive (click one to bet),
`/sg bet <player> <amount>` bets on the team of the player. The odds come from the [Elo ratings](configuration.md#statistics)
of the teams alive, minus the `margin` of the house (at most `max-odds`), and stay as they were when the bet was
placed. **Bets on the winners pay amount × odds**, the others are lost; a draw or a stopped round pays everything back.
Once only `close-at` teams are left, no more bets are taken. The players alive can't bet.

## Alliances

In games where everybody plays alone, `modules.alliances` (off by default) lets two players (`max-size`) team up:
`/sg ally <player>` offers an alliance, the same command of the other player (or a click on the offer) accepts it.
Allies can't hurt each other and the [tracker](#tracker) ignores them. `/sg ally leave` breaks the alliance; everybody
hears about the betrayal. All alliances break once only `break-at` players are left, in the deathmatch, or when nobody
but the allies is left.

## Force field

With `modules.force-field` (off by default) the world border throws back what hits it: arrows, tridents, snowballs and
ender pearls bounce off and fly back into the arena, players who run into it get a shock (`damage`) and are pushed
back.

## The fallen

With `modules.fallen` (off by default) everybody in the game hears a cannon shot when a player is out, and every
`minutes` minutes the fallen since the last list are announced (with a title). In a [Varo](varo.md) the list comes
with every new day.

## Training scores

With `modules.training-score` (off by default) everybody gets a score from 1 to 12 while they stand on their spawn
points: 6 for the start rating, one more (less) for every `points-per-score` rating points above (below) and up to
±`random` by the mood of the Gamemakers. The scores are announced, the best first.

## Reaping

With `modules.reaping` (off by default) more players than `max-players` can wait in the lobby (`applicants` ×
`max-players`). When the game starts, `max-players` of them are drawn; the others watch. Everybody has one lot, plus
`missed-lots` for every reaping they weren't drawn in (until they are) and n − 1 with the permission
`sg.reaping.lots.<n>` (2 to 10).

## Quells

With `modules.quell` (off by default) every `every`-th round of an arena (or by `chance`, in percent) is a Quell with
one of the `rules`. A rule changes settings of `survival-games` for that round only:

```yaml
rules:
  no-grace: { grace-period: 0 }
  nowhere-to-hide: { reveal: { minutes: 1, interval: 30 } }
  sudden-border: { world-border: { shrink-after: 1, shrink-minutes: 5 } }
  no-refill: { refill-minutes: 0 }
```

The players learn the rule in the lobby and when the game begins (its description is the message
`quell_rule_<name>`, otherwise its name). Admins choose the rule of the round in the lobby (otherwise of the next one)
with `/sg game <arena> quell <rule>`, also while the module is off.

## Gamemaker events

With `modules.arena-events` (off by default) the arena turns against the players every `min-minutes` to `max-minutes`
(after the grace period, not in the deathmatch) for `duration` seconds, announced `warning` seconds before:

| Event | What happens |
|-------|--------------|
| `poison-water` | the water poisons |
| `swarm` | `swarm-size` mobs (`swarm-mob`) attack every player, they disappear afterwards |
| `fog` | nobody sees far |
| `heat-wave` | hungry and slow under the open sky |

`events` lists the ones that can happen. Admins start one right away: `/sg game <arena> event <event>`.

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

- **Inventories:** who was killed gets a message with the health the killer had left; a click shows the killer's
  inventory, armor, health and effects of that moment. The killer can look at the victim's inventory, and at the end
  everybody at those of the winners (`modules.inventory-view`).
- **Fireworks** for the winners (`fireworks`).
- **Rewards:** money and commands for kills, wins and games, see [rewards](configuration.md#rewards).
- **Rating:** every player has an Elo rating per game type, see [statistics](configuration.md#statistics).
- **Replay:** the round has been recorded; a click on the message at the end (or `/sg replay`) plays it again in a
  copy of the map, see [replays](replays.md).
