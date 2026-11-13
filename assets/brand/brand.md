# PRISMARIN Brand Guide

Brand identity for the PRISMARIN network. Applies to every player-facing surface: chat, scoreboard, tablist, boss bars, MOTD, holograms, web.

## Palette

| Role | Hex | MiniMessage tag | Usage |
|---|---|---|---|
| Primary | `#22D3EE` | `<color:#22D3EE>` | Brand color, gradient start, highlights, selected states |
| Accent | `#2DD4BF` | `<color:#2DD4BF>` | Gradient end, positive values, confirmations |
| Background dark | `#0F172A` | `<color:#0F172A>` | Icon/web backgrounds, never for chat text |
| Gold | `#FBBF24` | `<color:#FBBF24>` | Ranks, coins, records, premium context only |
| Text muted | gray | `<gray>` | Body text after the prefix, secondary info |
| Text dimmed | dark_gray | `<dark_gray>` | Separators, brackets, timestamps |
| Text bright | white | `<white>` | Player names, values the eye should land on |
| Error | red | `<red>` | Failures, denied actions |

Brand gradient: `<gradient:#22D3EE:#2DD4BF>` — always in this direction, never reversed.

## Chat prefix

```
<gradient:#22D3EE:#2DD4BF><b>PRISMARIN</b></gradient> <dark_gray>|</dark_gray> <gray>{message}
```

Rules:
- The prefix is the only bold element in a system message.
- Message body starts `<gray>`; emphasized values switch to `<white>` or `<color:#2DD4BF>`.
- Confirmations use `%success_prefix%` (body light green `#86EFAC`), errors `%error_prefix%` (body light red `#FCA5A5`), both after the identical prefix.
- Subsystems never invent their own prefixes; one network, one prefix.

Examples:

```
PRISMARIN | Du wurdest mit der Lobby verbunden.
PRISMARIN | BedWars startet in 5 Sekunden.
PRISMARIN | Dein Freund Notch ist jetzt online.
```

## Scoreboard layout (lobby)

```
   ◆ PRISMARIN ◆          <- title: brand gradient + bold, ◆ in #22D3EE
                           <- blank spacer
   Rang                    <- label: gray
    ● Premium              <- value: #FBBF24, ● in rank color
   Coins                   <- label: gray
    ● 12.480               <- value: white
   Online                  <- label: gray
    ● 1.337 Spieler        <- value: white
                           <- blank spacer
   prismarin.net           <- footer: #2DD4BF
```

Labels gray, values white on the following indented line, one `●` bullet in `#22D3EE` or context color. Maximum 12 lines, at least two blank spacers.

## Tablist layout

```
Header:
  <gradient:#22D3EE:#2DD4BF><b>PRISMARIN</b></gradient>
  <gray>Lobby-1 <dark_gray>●</dark_gray> <white>1.337</white> <gray>Spieler online

Footer:
  <gray>Ping: <white>{ping}ms</white> <dark_gray>●</dark_gray> <gray>prismarin.net
```

Player entries are sorted by rank weight; rank tag before the name in the rank color, name in white.

## Gradient usage rules

- The brand gradient is reserved for the word PRISMARIN, scoreboard titles and primary headings.
- Never apply the gradient to body text, numbers or player names.
- Never stack the gradient with additional colors inside the same segment.
- On dark backgrounds (`#0F172A`) the gradient carries the identity; on light surfaces use `#0F172A` text with a gradient underline or the emblem instead.
- Long strings (MOTD line 1 is the only exception) do not get per-character gradients; they become unreadable.

## Do / Don't

| Do | Don't |
|---|---|
| Unicode glyphs `◆ ●` as markers, success and errors shown by text colour | Emojis of any kind, check marks or crosses |
| Concrete, data-driven text: "BedWars startet in 5 Sekunden." | Motivational slogans: "Dein Abenteuer beginnt!" |
| Numbers formatted with dots: `12.480` | Raw unformatted values: `12480` |
| One prefix, one gradient direction | Per-feature prefixes or reversed gradients |
| German default, complete English fallback, identical keys | Hardcoded strings in Java |
| `<white>` to highlight the one value that matters | Rainbow highlighting of half the sentence |

## Logo usage

The logo is the wave P: a pixel sprite whose bowl curls in like a wave, with spray pixels breaking off the crest. It is drawn on a 13 × 14 pixel grid in flat colours, no gradients.

- `logo.svg` — the sprite on its own grid. Scale it only by whole numbers (2×, 3×, 4×) with nearest-neighbour scaling (`image-rendering: pixelated`), so no pixel blurs.
- `wordmark.svg` — sprite plus the pixel lettering PRISMARIN in white, for dark backgrounds.
- `wordmark-on-light.svg` — the same with navy lettering and darker spray pixels, for white or light backgrounds.
- `server-icon.svg` / `server-icon.png` — the sprite at 4× on `#0F172A`, 64 × 64, for the Minecraft server list.
- `assets/listing/icon.png` — the sprite at 48× on `#0F172A`, 1024 × 1024, for Hangar, Spigot and GitHub.
- Clear space around the sprite: at least two sprite pixels on every side.
- Do not recolor, rotate, outline, smooth or anti-alias the sprite. Preferred backgrounds: `#0F172A` or white.
