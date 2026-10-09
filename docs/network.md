# Network

With a proxy (Velocity or BungeeCord) every game can get its own server. The players wait on a lobby server and are
sent to a free game server; after the game they come back.

```
                ┌──────────────┐
  players ────► │  proxy       │
                └──────┬───────┘
          ┌────────────┼─────────────┐
   ┌──────▼─────┐ ┌────▼───────┐ ┌───▼────────┐
   │ lobby      │ │ sg-1       │ │ sg-2       │
   │ mode: lobby│ │ mode: game │ │ mode: game │
   └────────────┘ └────────────┘ └────────────┘
```

## The proxy

Add the servers to the proxy as usual. The plugin sends players with the plugin messages of the `BungeeCord` channel –
on Velocity turn on `bungee-plugin-message-channel = true` in `velocity.toml` (the default).

## Game servers

Every game server runs the plugin with the same arena:

```yaml
network:
  mode: game
  lobby-server: lobby   # name of the lobby server in the proxy config (empty: players are kicked)
  arena: castle         # the arena of this server
```

- The game of the arena is created on start; there are no other games on the server.
- Players who connect join the game right away – or watch it while it is running.
- Players who can't play are turned away **before** they connect: the lobby is full, the game is running and nobody
  may watch, they are out of the Varo or have no play time left today. Operators and players with
  `sg.network.bypass` always get in.
- After the game, with `/sg leave` or when the play time is over, players are sent to `lobby-server`.
- The **MOTD** of the server shows the state of the game (`SG;LOBBY;3;12;castle;survival-games`) – the lobby server
  reads it.

!!! tip "Same maps everywhere"
    Copy `plugins/SurvivalGames` (with `arenas.yml` and `maps/`) to all game servers, then set `network.arena` on each.

## Lobby server

```yaml
network:
  mode: lobby
  servers:
    - name: sg-1                  # name in the proxy config
      address: localhost:25566    # address the lobby pings for the state of the game
    - name: sg-2
      address: localhost:25567
```

- The lobby server has no games. It pings the game servers every 2 seconds.
- `/sg join` sends the player to the fullest server whose game is waiting, `/sg join <server>` to that one,
  `/sg spectate` to a running game. `/sg list` shows all servers.
- **Signs** with `[sg]` in the first line and the server name in the second show the state of that server and send
  players there.
