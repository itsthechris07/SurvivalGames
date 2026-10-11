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

## Optional rules

All of them are in `varo` in `config.yml` and off by default (except the villager) – change them there or with
[`/sg settings varo`](configuration.md#changing-settings-in-the-game).

| Option | Rule |
|---|---|
| `peaceful-days` | No PvP during the first days of the game – time to farm and build. Everybody is told when the peaceful days are over. |
| `inactive-days` | Players who haven't played for this many days in a row are out (also players who never showed up) – nobody blocks the end of the game. |
| `coordinates-from-day` | From this day on the positions of all players alive are announced at the start of every day (of players outside of the game: where they left it). |
| `offline-npc-seconds` | **No escape by leaving the server:** a villager with the name and the health of the player stands in for them for this many seconds (default: 60). Enemies can hurt it; if they kill it, the player is out and their items drop. Damage it took stays when the player comes back. Not for `/sg leave`. |

### Banned items

`modules.restrictions` (used in Varo by default) keeps items out of the game:

```yaml
modules:
  restrictions:
    banned-items: [ totem_of_undying, end_crystal ]
    banned-potions: [ strength ]        # also strong_strength and long_strength
    max-enchantments: { sharpness: 3, knockback: 0 }   # 0: banned
```

Banned items can't be crafted, picked up or used and are taken out of the inventories; banned potions can't be brewed
or drunk; enchantments above their limit are lowered – also on items that are already in the game.

## Strikes

Who breaks the rules gets a strike from an admin: `/sg game <arena> strike <player> [reason]`. Everybody in the game
sees it, and `varo.strikes` decides what happens at the 1st, 2nd, 3rd, ... strike:

| Consequence | |
|---|---|
| `coordinates` | the position of the player is announced (their saved one if they are not in the game) |
| `inventory` | their inventory in the game is cleared |
| `out` | they are out of the game |
| `warning` | nothing else (also every strike beyond the list) |

`/sg game <arena> unstrike <player>` takes back the last strike (not its consequence), `/sg game <arena> strikes`
lists everybody with strikes. Players see their own with `/sg strikes`, the scoreboard shows the number.

## Saved for days

The game is saved in `games/<arena>.yml` and its world is kept – restarts don't change anything. `/sg game <arena>
stop` stops it (it continues with `/sg arena <arena> creategame`).

`/sg game <arena> reset --confirm` starts again from the lobby with a fresh world (everybody alive, the teams stay);
`--teams` deletes the teams too. Deleting the arena deletes the saved game and its world.

### Backups

When the game begins and at the start of every day, the world and the saved game are zipped into
`plugins/SurvivalGames/backups/<arena>/<day>.zip` (in the background). The newest `varo.backups` files are kept
(default: 7, `0`: no backups).

Admins make a backup at any time with `/sg game <arena> backup` (`<day>_<time>.zip`, also with `varo.backups: 0`).

The backup of a day is also the world its [replay](replays.md#varo) is played in – every day of a Varo is recorded
(`/sg replay`, while the Varo is running only for admins).

`/sg game <arena> backups` lists the backups, `/sg game <arena> restore <backup> --confirm` goes back to one: everybody
leaves the game for a moment, the world and the saved game are replaced by those of the backup and the players who
may play join again. Everything that happened since the backup is lost – bounties stay as they are, their money
has been taken already.

By hand (server stopped): replace the world folder of the game (`sgvaro_<arena>` in the dimensions of the main world)
with the folder `world` of the zip file and `games/<arena>.yml` with its `game.yml`.

## Extras

The extras can be turned off or used in other game types (`modules` in `config.yml`, `games: [ varo, ... ]`).

| Extra | How it works |
|---|---|
| **Backpack** | Crafted from leather around a chest with a diamond at the bottom. Right click opens it – the team shares one backpack (`type: team`) or everybody has their own (`player`). |
| **Chest lock** | Put a sign with `[Lock]` in the first line onto a chest, barrel, furnace, ... – only your team can open and break it, hoppers and explosions leave it alone. |
| **Gravity grenade** | Crafted from a snowball, TNT and an ender pearl. Thrown, it tears out the blocks where it lands and pulls them and nearby players into one point. |
| **Toxic rain** | Some rains (`chance`) hurt everybody under the open sky – but never kill. |
| **Tracker** | A compass that points at the nearest enemy, given on the spawn point (only in Survival Games by default, `modules.tracker.games`). |
| **Supply drop** | A chest with the supply loot that lands near the center (only in Survival Games by default). |
| **Bounty** | Players put money on an enemy, the killer gets it – see [bounties](survival-games.md#bounties). Bounties of a Varo are saved with the game. |
| **Inventory view** | After a kill the victim can look at the inventory of the killer (and the other way round), at the end everybody at those of the winners. |
| **Kits**, **sponsors** | Only in Survival Games by default (`modules.kits.games`, `modules.sponsor.games`). |
