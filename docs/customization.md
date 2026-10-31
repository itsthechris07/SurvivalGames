# Messages and languages

## Language

Set `language` in `config.yml` and run `/sg reload`:

- `en` – English (`messages.yml`)
- `de` – German (`messages_de.yml`)
- your own language: put a translated `messages_<language>.yml` (e.g. `messages_fr.yml`) into `plugins/SurvivalGames` and set
  `language: fr`. Texts missing in your file are English.

## Changing texts

All texts are in the messages file of your language. They use [MiniMessage](https://docs.papermc.io/adventure/minimessage/format):

```yaml
team_full: '<red>This team is full.'
game_won_solo: '<yellow>{0} <gold>has won in {1}<gold>!'
```

- Colors and formats: `<red>`, `<bold>`, `<#ff8800>`, `<gradient:gold:red>`, ... Old color codes (`&c`) still work.
- `{0}`, `{1}`, ... are replaced by names, numbers, ... Names of players are inserted as plain text.
- Write `\<` for a normal `<`.
- Several lines of item texts are separated by `\n`.
- The prefix of all messages is `prefix` in `config.yml` (empty: none). Every game type can have its own: `prefix` in its
  section (`survival-games`, `varo`, `uhc`, `duels`), e.g. `uhc.prefix: "<dark_gray>[<red>UHC<dark_gray>]"`. It is used for
  the messages of the game and for everything a player in that game gets (commands, kits, bounties, ...); empty
  uses `prefix`. `/sg reload` applies a change.

## Clickable commands

Commands in messages can be clicked:

- `<run:"/sg team list">/sg team list</run>` runs the command
- `<suggest:"/sg team create ">...</suggest>` puts it into the chat box (for commands that need more input)

When you update the plugin, new texts are added to your file – texts you changed stay as they are.
