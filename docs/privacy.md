# Privacy

SurvivalGames sends anonymous data to help develop the plugin and checks for updates. All of it can be turned off.

## Statistics (bStats)
[bStats](https://bstats.org/plugin/bukkit/SurvivalGames-Varo/34655) collects anonymous statistics: the number of
servers and players, the server and Java version, the settings of the plugin (game types, extras, optional plugins)
and how often games and features are used. No names of players, arenas, kits or teams are sent, and IP addresses are
not stored.

**Turn off:** set `enabled: false` in `plugins/bStats/config.yml` (turns bStats off for all plugins).

## Error reports (Sentry)
When an error occurs in the code of SurvivalGames, a report is sent to [Sentry](https://sentry.io) (Functional
Software, Inc., data stored in the USA; Sentry is certified under the EU-U.S. Data Privacy Framework, see
[sentry.io/privacy](https://sentry.io/privacy/)). It contains the error with its stack trace, the version of
SurvivalGames, the server software and version, the Java version, the network mode and the game types the server has
arenas for. To find errors that only happen under load, it also contains the state of the server at that moment, as
numbers only: players online and in games, spectators, the running games per game type and phase, TPS and MSPT,
memory, and the number of worlds, loaded chunks, entities and plugins.

To understand how an error came about, a report also contains:

- the games: game type, arena, phase, play time or Varo day, the number of participants, survivors, spectators and
  teams, the team size and the number of spawns (for an error in a game, that game in detail),
- the last 30 events of SurvivalGames: games changing their phase, "a player is out", features and ways to join a
  game, reloads, and the `/sg` commands that were used, only with their first word (e.g. `sg join`, never the
  arguments),
- the other plugins and their versions,
- the exact build of the server, how long it has been running, whether SurvivalGames was loaded while the server was
  running (`/reload`, plugin managers), online mode, view and simulation distance, the maximum number of players,
  the language and the storage of the statistics,
- the system: operating system, CPU cores, Java vendor and version, maximum memory.

If the code of SurvivalGames blocks the server thread for a second or longer (`sentry.lag-threshold`), a report shows
where it hangs, with the same information.

`/sg report <text>` sends your description of a problem with the same information and links it to the last error
report of the server.

The settings of the plugin are attached (`config.yml` and `loot.yml`, without comments), so the error can be
reproduced with the same setup. They contain the names you gave arenas, kits, teams and worlds. Before they are sent,
passwords, database logins, webhooks, the addresses of servers and databases, UUIDs and the names of online players
are replaced with `<redacted>`, `<uuid>` and `<player>`.

It does **not** contain: player names or UUIDs (they are removed from the error messages), the name or address of
the server, logs, `arenas.yml`, statistics, or errors of other plugins. The Sentry project does not store IP
addresses. Every error is reported once per server start, at most 25 per start.

`/sg debug sentry` sends a test report, so you can check that reports get through.

**Turn off:** set `sentry.enabled: false` in `plugins/SurvivalGames/config.yml`. To send the reports without the
settings, set `sentry.include-config: false`; for no lag reports, set `sentry.lag-threshold: 0`.

## Update checker (GitHub)
When the server starts and every 6 hours, SurvivalGames requests the list of its releases from the GitHub API
(`api.github.com`, GitHub, Inc., USA) to tell admins about new versions. Nothing about the server or its players is
sent; like every web request, GitHub sees the IP address of the server and the installed version of SurvivalGames
(user agent), see [GitHub's privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).

**Turn off:** set `update-checker.enabled: false` in `plugins/SurvivalGames/config.yml`.

Legal basis (GDPR): legitimate interest in finding and fixing errors and in keeping the plugin up to date
(Art. 6 (1) (f)); you can object at any time by turning off the reports and the update checker as described above.
