# Integrations

SurvivalGames works on its own. If one of these plugins is installed, it is used automatically – nothing has to be
turned on.

| Plugin | What it is used for |
|---|---|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) or [VaultUnlocked](https://modrinth.com/plugin/vaultunlocked) + an economy plugin | [money](#economy): rewards, bounties, sponsors |
| [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi), [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) | the [placeholders](placeholders.md) |
| [Parties](https://modrinth.com/plugin/parties), [Party and Friends](https://www.spigotmc.org/resources/party-and-friends-for-spigot.11633/) | [parties](#parties) join and play together |
| [Citizens](https://citizensnpcs.co/), [FancyNpcs](https://modrinth.com/plugin/fancynpcs) | [join NPCs](#join-npcs) |
| [Floodgate](https://geysermc.org/download#floodgate) | [Bedrock players](#bedrock-players) get forms instead of chest menus |
| [TAB](https://modrinth.com/plugin/tab-was-taken) | the tab list, name tags and sidebar of the game aren't overwritten |
| [EssentialsX](https://essentialsx.net/) | no god mode, flying, vanish or teleports into games |
| SuperVanish, PremiumVanish, CMI, ... | vanished players aren't put into games |
| [Multiverse-Core](https://modrinth.com/plugin/multiverse-core), Multiverse-Inventories | the worlds of games are left alone |

## Economy

With Vault (or VaultUnlocked) and an economy plugin like EssentialsX:

- **[Rewards](configuration.md#rewards):** money for kills, wins and games (`rewards.enabled`).
- **[Bounties](survival-games.md#bounties):** players put money on an enemy, the killer gets it.
- **[Sponsors](survival-games.md#sponsors):** spectators buy items for the players alive.

If the economy plugin supports the new API of VaultUnlocked, it is used – otherwise the one of Vault.

## Parties

`parties` in `config.yml`:

- `join-together`: when the leader of a party joins a game (`/sg join`, a join sign or a join NPC), the members on
  the same server come along – as long as there is room.
- `same-team`: with teams, the members of a party play in the same team: in the team one of them chose, otherwise in
  a team with room for all of them.

In a [network](network.md) the party plugin on the proxy takes the members along when the leader is sent to a game
server. For Party and Friends install "Spigot Party API for Party and Friends" on the game servers.

## Join NPCs

An NPC of Citizens or FancyNpcs can work like a join sign:

1. Create the NPC with its plugin.
2. `/sg npc link <arena>` (on a lobby server: `<server>`) and click the NPC.

A click on the NPC joins the game (or watches it while it runs). With `npcs.show-status` the NPC shows the state of
the game and the players: Citizens in an extra hologram line, FancyNpcs behind the display name.
`/sg npc unlink` removes the link, `/sg npc list` lists all join NPCs (saved in `npcs.yml`).

## Bedrock players

Players who join through [Geyser](https://geysermc.org/) and Floodgate see the menus of the plugin (teams, kits, map
voting, sponsors, settings, ...) as **forms** instead of chest menus, and type numbers into a form.

## TAB

While a player is in a game, TAB doesn't overwrite the name in the tab list, the name tags (team colors) and the
sidebar of the game. Everything of TAB comes back when they leave the game.

## EssentialsX

- God mode, vanish and AFK are turned off when a player joins a game – god mode and vanish come back afterwards.
- `/god`, `/fly` and `/vanish` don't work while playing.
- Teleports of Essentials (`/back`, `/tpa`, `/home`, ...) don't lead into the worlds of games.

## Vanish plugins

Vanished players (SuperVanish, PremiumVanish, EssentialsX, CMI, ...) aren't put into games automatically (game
server, party members) and can only play once they are visible again.

## Multiverse

- **Multiverse-Core** doesn't import the worlds of the games (`sg_*`, `sgvaro_*`), so it doesn't change the game mode
  there or load them after a restart.
- **Multiverse-Inventories** doesn't swap the inventories of players who are in a game – the plugin saves and
  restores them itself.
