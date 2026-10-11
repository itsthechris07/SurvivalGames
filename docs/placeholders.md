# Placeholders

With [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) (or [MiniPlaceholders](#miniplaceholders)) installed, the plugin adds these placeholders – e.g.
for tab lists, scoreboards of other plugins or holograms. They are about the game of the player: the one they are in,
or a Varo they take part in while they are outside of it. Without a game they are empty.

## Server

| Placeholder | Value |
|---|---|
| `%sg_games%` | the number of games on this server |
| `%sg_game_players_<arena>%` | players in the game of an arena, e.g. `%sg_game_players_castle%` (on a lobby server: of a game server) |
| `%sg_game_max_players_<arena>%` | its maximum number of players |
| `%sg_game_state_<arena>%` | its state, like on the join signs |

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
| `%sg_strikes%` | the number of strikes of the player (Varo) |

## Statistics

These work without a game, for online players. `<stat>` is `games`, `wins`, `kills`, `deaths` or `rating` (the
[Elo rating](configuration.md#rating)).

| Placeholder | Value |
|---|---|
| `%sg_stats_<stat>%` | the statistic of the player in all game types, e.g. `%sg_stats_wins%` |
| `%sg_stats_<type>_<stat>%` | the statistic in one game type, e.g. `%sg_stats_varo_kills%` |
| `%sg_stats_kdr%`, `%sg_stats_<type>_kdr%` | kills per death, e.g. `1.50` |
| `%sg_top_<stat>_<rank>_name%` | the player on rank 1-10 of all game types, e.g. `%sg_top_wins_1_name%` (updated every minute) |
| `%sg_top_<stat>_<rank>_value%` | their value |
| `%sg_achievements%` | the number of [achievements](configuration.md#achievements) of the player |
| `%sg_achievements_total%` | the number of achievements there are |

## MiniPlaceholders

With [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) the same placeholders exist as MiniMessage
tags – with arguments instead of underscores:

| Tag | Like |
|---|---|
| `<sg_arena>`, `<sg_kills>`, `<sg_time_left>`, ... | the placeholders of the game and the player (of the viewer) |
| `<sg_games>` | `%sg_games%` |
| `<sg_game_players:castle>`, `<sg_game_max_players:castle>`, `<sg_game_state:castle>` | `%sg_game_..._castle%` |
| `<sg_stats:wins>`, `<sg_stats:varo:kills>` | `%sg_stats_wins%`, `%sg_stats_varo_kills%` |
| `<sg_top:wins:1:name>`, `<sg_top:wins:1:value>` | `%sg_top_wins_1_name%`, `%sg_top_wins_1_value%` |
| `<sg_achievements>`, `<sg_achievements_total>` | `%sg_achievements%`, `%sg_achievements_total%` |
