# Replays

The games are recorded while they are played and can be watched again afterwards: the players with their skins, what
they hold and wear, their hits and deaths, arrows and other projectiles, the blocks that change, the world border, the
time of day and the messages of the game (kills, deathmatch, ...). Chat messages of the players are **not** recorded.

A replay runs in its own world – a fresh copy of the map – so nothing disturbs the games that are running. No client
mod is needed: the players of the recording are shown as mannequins.

## Watching

| Command | |
|---|---|
| `/sg replay` | the menu with the recordings – while you watch one: its controls |
| `/sg replay list [arena]` | the newest recordings in the chat (click one to watch it) |
| `/sg replay play <id>` | watches a recording |
| `/sg replay pause` | pauses the replay or continues it |
| `/sg replay speed <speed>` | `0.25`, `0.5`, `1`, `2`, `4` or `8` |
| `/sg replay skip <seconds>` | jumps forward, with a negative number back (`-10`) |
| `/sg replay goto <time>` | jumps to a time: seconds (`90`) or minutes and seconds (`1:30`) |
| `/sg replay players` | the players who are in the game at that moment – click one to watch through their eyes (sneak to stop) |
| `/sg replay leave` (or `/sg leave`) | leaves the replay |
| `/sg replay delete <id>` | deletes a recording (admins) |

After a game everybody in it gets a message with a button to watch its replay.

You are a spectator in the replay: fly around freely, your inventory and your location are saved before and given back
when you leave. Several players can watch the same recording together – they are in the same replay and everybody
controls it, the others are told who paused or jumped. When the last viewer leaves, the world of the replay is removed.

Watching needs the permission `sg.replay` (everybody has it by default).

## Survival Games

Every round is one recording, from the moment the players are let go until 3 seconds after the winner is known. Its id is
the arena and the time it began, e.g. `forest-20261010-183000`. Rounds shorter than 5 seconds aren't kept.

Arenas with a generated world are generated again with the same seed for the replay.

## Varo

A Varo gets **one recording per day** (`<arena>-day-<date>`, e.g. `varo-day-2026-10-10`). The time of the recording
only runs while somebody plays, so the play times of the day follow each other without the hours in between – the bar
above the hotbar also shows the time of day it was.

The replay of a day is played in the [backup](varo.md#backups) of the world from the beginning of that day, so the
bases the players built before are there. Keep at least as many backups (`varo.backups`) as recordings
(`replays.keep`) – without the backup the replay shows the world as it was when the Varo began.

!!! note
    While a Varo is running only admins (`sg.admin`) can watch its recordings: they show where everybody is and what
    they have. When it is over, everybody can.

## Settings

```yaml
replays:
  enabled: true
  # the game types that are recorded
  games:
    - survival-games
    - varo
  # the newest recordings that are kept per arena, 0: all of them
  keep: 10
  # the replays that can be watched at the same time - every one has its own world
  max-playbacks: 2
```

The recordings are saved as compressed files `plugins/SurvivalGames/replays/<id>.sgr`. In a [network](network.md)
the recordings are on the server the game was played on.

## What a replay doesn't show

- chat messages of the players and the contents of chests and inventories
- animals and monsters, dropped items and particles
- what the players eat or drink, and how they draw a bow (the arrow is shown)
- redstone that switches by itself and blocks that change without a player, an explosion, fire, water or growth
  causing it
