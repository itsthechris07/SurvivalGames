# Placeholders

With [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) installed, the plugin adds these placeholders – e.g.
for tab lists, scoreboards of other plugins or holograms. They are about the game of the player: the one they are in,
or a Varo they take part in while they are outside of it. Without a game they are empty.

## Server

| Placeholder | Value |
|---|---|
| `%sg_games%` | the number of games on this server |

## Game

| Placeholder | Value |
|---|---|
| `%sg_arena%` | the name of the arena (display name) |
| `%sg_type%` | Survival Games or Varo |
| `%sg_state%` | Lobby, Starting, Running or Over (from the messages) |
| `%sg_players%` | players in the game |
| `%sg_max_players%` | the maximum number of players |
| `%sg_teams_alive%` | teams with players left |
| `%sg_players_alive%` | players left |
| `%sg_winner%` | the winner (empty while the game runs) |
| `%sg_day%` | the day of a Varo (0 in the lobby) |

## Player

| Placeholder | Value |
|---|---|
| `%sg_team%` | the team of the player |
| `%sg_kills%` | the kills of the player in the game |
| `%sg_alive%` | `true` if the player is still in the game |
| `%sg_time_left%` | the play time left today (Varo) |
