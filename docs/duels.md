# Duels

A duel is one against one (or team against team) on a small map: everybody gets the same kit, the last one standing
wins and gains [rating](commands.md). The settings are in `duels` in [`config.yml`](configuration.md).

## Setup

```
/sg arena create duel1 --type duels
/sg arena duel1 edit
```

A duel arena needs a map with **two spawn points** (`/sg arena duel1 spawn add` in the edit mode) and a lobby (the
[lobby world](getting-started.md#the-lobby-world) or `/sg arena duel1 lobby`). Several duels can run at the same time:
create more arenas, they may share the same map (`--map <map>`).

The kit is a normal [kit](survival-games.md#kits): fill your inventory and save it with `/sg kit create <name>`, then
set `kit: <name>` in `duels`. Without a kit everybody starts with an empty inventory.

For 2 vs 2 set `team-size: 2` and `max-players: 4` (and four spawn points).

## Playing

| Command | |
|---|---|
| `/sg duel` | waits for an opponent – in the duel somebody waits in already, otherwise in a free one |
| `/sg duel <player> [kit]` | challenges a player, optionally with another kit |
| `/sg duel accept <player>` / `deny <player>` | accepts / denies a challenge (also by clicking in the chat) |

A challenge is valid for `invite-seconds` seconds. When it is accepted, both players join a free duel arena that is
reserved for them – nobody else can join it. If one of them leaves the lobby, the duel is open for everybody again.

The duel begins `lobby-countdown` seconds after both players are there, they stand frozen for `start-countdown`
seconds. After `ending-seconds` everybody leaves the arena and it is free for the next duel. Join signs and NPCs work
like for the other game types – a sign of a duel arena is a queue for that arena.
