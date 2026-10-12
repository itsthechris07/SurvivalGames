# Branding

Images for the plugin pages on SpigotMC, Modrinth and Hangar.

| File | Size | Use |
|---|---|---|
| `icon.png` | 512×512 | Project icon (Modrinth, Hangar, SpigotMC) |
| `banner.png` | 1600×500 | Header at the top of the description |
| `how-it-works.png` | 1600×900 | Gallery / description: the two game types (Survival Games round, Varo days) |
| `features.png` | 1600×900 | Gallery / description: the server side (maps, menus, statistics, replays, integrations) |
| `gameplay.png` | 1600×900 | Gallery / description: what happens in the games (voting, kits, loot, Varo rules, extras) |

Copies for the documentation are in `docs/assets/` – copy them again after a change.

## Editing

The sources are in `design/` (one `.dc.html` per image, `canvas.json` holds the sizes). They are plain HTML with
inline styles in the same format as CatchTheBeacon (fonts: Chakra Petch, Barlow and Pixelify Sans for the scoreboard
from Google Fonts); the pixel art (sword, heart, arena map) is inline SVG.

After a change, render the PNGs again (needs Chrome or Edge):

```powershell
.\branding\render.ps1
```

Colors: background `#121014`, panels `#1B1820`, borders `#332C38`, Survival Games amber `#F5B83D`,
Varo red `#E5484D`, blue `#4B95FF`, green `#3FD08A`, violet `#9B6BFF`.
