# Survival Games

The default game type. The settings are in `survival-games` in [`config.yml`](configuration.md); arenas override
the number of players, the team size and the world border.

## Lobby

The players wait in the [lobby world](getting-started.md#the-lobby-world) or the lobby of the arena: nobody can break blocks, take damage or get hungry. With teams
(`team-size` > 1) they choose a team with the team item or `/sg team`; who has no team at the start gets one.

Once `min-players` are there, the **lobby countdown** (`lobby-countdown`) starts. When the game is full it is
shortened to `full-countdown`. If too many players leave, it stops.

### Map voting

With `map-voting.enabled` the players choose the map: the vote item (or `/sg vote [arena]`) lists the arena of the
game and up to `map-voting.maps` - 1 other arenas of Survival Games with the same team size that nobody is waiting in.
`map-voting.end-seconds` before the start the votes are counted and everybody moves into the arena that has won (a
draw: the arena of the game stays).

- Players with the permission `sg.vote.2` ... `sg.vote.5` have 2 ... 5 votes.
- The map of the last round can't be chosen again right away (`map-voting.block-last`) – unless it is the only one.
- There is nothing to vote for with only one arena, and on a [game server](network.md) (one arena per server).

### Kits

Admins save their inventory as a kit: `/sg kit create <name>` – armor and off hand included, the item in the hand
is the icon in the menu. With `--permission` only players with `sg.kit.<name>` can choose it. `/sg kit edit <kit>`
puts a kit into your (empty) inventory to change it, `/sg kit delete <kit>` deletes it.

The players choose their kit in the lobby with the kit item or `/sg kit [kit]` and get it when the game begins.
`modules.kits.default` is the kit of everybody who didn't choose one. The kits are saved in `kits.yml`.

## Start

Everybody is put onto a spawn point (or into the spawn circle) with an empty inventory and stands still until the
**start countdown** (`start-countdown`) is over. Then the round begins.

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
`world-border.shrink-after` minutes after the start – everybody is warned a minute before – down to
`world-border.final-size` blocks within `world-border.shrink-minutes`. Outside of it players take
`world-border.damage` per block and second. With `shrink-after: 0` it only shrinks in the deathmatch.

## Tracker

When the grace period is over, everybody alive gets a **player tracker**: a compass that points at the nearest enemy.
Holding it shows the name and the distance in the action bar (`modules.tracker`).

## Supply drops

Every `modules.supply-drop.minutes` a supply drop is announced with its coordinates near the center. A column of
sparks marks the place, `warning` seconds later a chest with the `supply` loot of [loot.yml](configuration.md#loot)
lands there – worth a fight. Not in the deathmatch.

## Lightnings

Nobody can hide until the end: after `reveal.minutes`, or once only `reveal.players` are left, a lightning (without
damage) strikes where every player alive is – again every `reveal.interval` seconds. `interval: 0` turns it off; in
the deathmatch there are no lightnings.

## Bounties

With an economy plugin ([Vault](integrations.md#economy)) the players put money on a player alive:
`/sg bounty <player> <amount>` (at least `modules.bounty.min`, at most `max`). The money is taken right away and
everybody sees the bounty. **Whoever kills the player gets all of it.** If they die otherwise, win or the round is
stopped, everybody gets their money back. `/sg bounty` lists all bounties, `/sg bounty <player>` shows one.

No bounties on yourself, on team mates and on players who are out. In a [Varo](varo.md) the bounties are saved with
the game.

## Sponsors

Spectators – also the players who are out – send items to the players alive: `/sg sponsor` opens the menu (the
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

- **Inventories:** who was killed gets a message with the health the killer had left – a click shows the killer's
  inventory, armor, health and effects of that moment. The killer can look at the victim's inventory, and at the end
  everybody at those of the winners (`modules.inventory-view`).
- **Fireworks** for the winners (`fireworks`).
- **Rewards:** money and commands for kills, wins and games – see [rewards](configuration.md#rewards).
- **Rating:** every player has an Elo rating per game type, see [statistics](configuration.md#statistics).
- **Replay:** the round has been recorded – a click on the message at the end (or `/sg replay`) plays it again in a
  copy of the map, see [replays](replays.md).
