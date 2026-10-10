# FAQ and troubleshooting

## "The game can't start"

The message tells why:

- **only X of Y players are there** – wait for more players, or test with `/sg game <arena> start --force` (one
  player is enough then).
- **the game has already started** – a Varo goes on until it is over or reset (`/sg game <arena> reset`).

## The game of an arena can't be created

`/sg arena <arena> creategame` tells why: the map is missing in `maps/`, the arena is being edited
(`/sg arena save` first), it already has a game or the server is the lobby server of a network (no games there).

## A player can't join

- Survival Games: the game is full or already running – they can watch with `/sg spectate` (`spectators`).
- Varo: their play time for today is over (the message tells when they can play again), they are out and
  `spectators` is `false`, or the game is running and they aren't part of it.
- On a game server of a network the same checks happen before they connect – see [Network](network.md).

## Does the plugin change my normal world?

No. Every game plays in a copy of its map (Survival Games: a new copy every round, Varo: one copy for the whole game).
The inventory, location, health, ... of a player are saved when they join a game and given back when they leave –
also after a crash of the server.

## When does a new Varo day start?

At `varo.day-start-hour` (server time, default 0 = midnight). Restarting the server doesn't change anything – the play
time is saved per day.

## A player died by mistake (lag, bug, ...)

`/sg game <arena> revive <player>` brings them back into the game. Their items are gone – give them back with `/give`
if needed.

## Can I change the settings during the game?

Yes: change `config.yml` and run `/sg reload`. Arenas can't be changed while they have a game
(`/sg arena <arena> removegame` first).

## I want to play Varo again

`/sg game <arena> reset --confirm` starts again from the lobby with a fresh world, everybody is alive again (the teams
stay). To start with new teams: `/sg game <arena> reset --teams --confirm`.

## Bounties and sponsors say they need an economy plugin

Install [Vault](integrations.md#economy) (or VaultUnlocked) **and** an economy plugin (e.g. EssentialsX). Free items
of the sponsors (`price: 0`) and the reward commands work without one.

## A player left the server during a fight in a Varo

A villager with their name stands in for them for `varo.offline-npc-seconds`. If an enemy kills it, the player is
out and their items drop – see the [optional rules](varo.md#optional-rules). `0` turns it off.

## An item, potion or enchantment is too strong for our Varo

Ban it: `modules.restrictions` in `config.yml`, see [banned items](varo.md#banned-items).

## How do I go back to yesterday in a Varo?

Every day the world and the game are saved as a zip file – see [backups](varo.md#backups).

## The leaderboard hologram or sign is empty

The top lists are updated every minute and need `stats.enabled: true`. The rating is only counted in the game types
of `stats.rating.games`.
