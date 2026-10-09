# Varo

A Varo runs for days in the same world. The settings are in `varo` in [`config.yml`](configuration.md).

## Before the start

Create the arena with `--type varo` – a generated world (`--generate`) is a good choice. The players join the game
(`/sg join <arena>` or a sign) and create teams with `/sg team create <name> [color]` or the team item, the others join
with a click or `/sg team join <team>`. Admins can manage the teams with `/sg game <arena> team ...`.

`/sg game <arena> start` starts the game: after `start-countdown` seconds everybody stands on their spawn point (the
members of a team next to each other) and the game runs. Players without a team get one.

## Playing every day

Players come back to the game with `/sg join <arena>` (or the sign) and leave it with `/sg leave`. **Outside of the
game they get their normal inventory and location back** – inside the game they continue where they left.

- **Play time:** every player may play `play-time` minutes per day. The time only runs while they are in the game. It
  is shown on the scoreboard, with warnings before the end; `/sg time` shows it too. When it is over, the player
  leaves the game (on a [game server](network.md): back to the lobby) and can't join again until the next day.
- **No escape from a fight:** while an enemy is within `combat-radius` blocks, the play time doesn't run out.
- **Join protection:** after joining, a player can't be hurt for `join-protection` seconds (unless they attack).
- **A new day** starts at `day-start-hour` (server time). Everybody has their full play time again and the **world
  border** shrinks by `world-border.shrink-per-day` blocks until `world-border.min-size`.

## One life

The first death is the last one: the player is out. With `spectators: true` they can watch (but not while they are
still alive in the game – no scouting). When the last player of a team is out, the team is out. The last team
standing wins.

Admins can bring a player back with `/sg game <arena> revive <player>`.

## Saved for days

The game is saved in `games/<arena>.yml` and its world is kept – restarts don't change anything. `/sg game <arena>
stop` stops it (it continues with `/sg arena <arena> creategame`).

`/sg game <arena> reset --confirm` starts again from the lobby with a fresh world (everybody alive, the teams stay);
`--teams` deletes the teams too. Deleting the arena deletes the saved game and its world.

## Extras

The extras can be turned off or used in other game types (`modules` in `config.yml`, `games: [ varo, ... ]`).

| Extra | How it works |
|---|---|
| **Backpack** | Crafted from leather around a chest with a diamond at the bottom. Right click opens it – the team shares one backpack (`type: team`) or everybody has their own (`player`). |
| **Chest lock** | Put a sign with `[Lock]` in the first line onto a chest, barrel, furnace, ... – only your team can open and break it, hoppers and explosions leave it alone. |
| **Gravity grenade** | Crafted from a snowball, TNT and an ender pearl. Thrown, it tears out the blocks where it lands and pulls them and nearby players into one point. |
| **Toxic rain** | Some rains (`chance`) hurt everybody under the open sky – but never kill. |
