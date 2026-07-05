# Modul `:api`

## Anforderungen

- Plattformneutrale Verträge für alle Netzwerk-Dienste: kein Paper, kein Velocity, kein Guice, keine Treiber.
- Einzige externe Abhängigkeit ist Adventure (`adventure-api`, `adventure-text-minimessage`) als `compileOnly`, weil beide Plattformen es bündeln.
- Alle I/O-Verträge sind asynchron (`CompletableFuture`); Implementierungen liegen in `:shared`.
- JavaDoc auf allen öffentlichen Typen und Methoden — das ist das einzige Modul mit dokumentierter Public-API.

## Architektur

Reine Interfaces, Records und eine Konstantenklasse. Das Modul wird in `PrismarinCore.jar` eingebettet und in `PrismarinVelocity.jar` geshadowt; abhängige Paper-Module referenzieren es nur `compileOnly`. Es gibt keine statischen Zugriffe außer `Brand` — Instanzen werden per Guice (Paper) bzw. Konstruktor (Velocity) verdrahtet.

## Klassenstruktur

```
net.prismarin.api
├── brand
│   └── Brand                Konstanten NAME, PRIMARY, ACCENT, BG_DARK, GOLD, TEXT_MUTED, PREFIX; gradient(String)
├── economy
│   ├── Currency             Enum COINS, GEMS mit MiniMessage-Tokens display()/symbol()
│   ├── EconomyResult        Record (success, newBalance, failureReason) + Factory success()/failure()
│   └── EconomyService       balance/deposit/withdraw, atomar, CompletableFuture
├── locale
│   └── LocaleService        render(key, locale, resolvers) -> Component, raw(key, locale) -> String
├── messaging
│   ├── MessageBus           publish/subscribe/close/serverId, JSON-Payloads
│   └── NetworkMessage       Record-Envelope (id, channel, sourceServer, timestamp, payloadJson)
├── profile
│   ├── PlayerProfile        Mutable Profil: uuid, name, firstJoin, lastSeen, locale, coins, gems, level, xp, settings; addXp/xpForNextLevel
│   └── ProfileService       load/getCached/save/unload/modify
└── server
    ├── NetworkServer        Record (name, displayName, host, port, category) mit Enum Category LOBBY/GAME/BUILD
    └── ServerDirectory      servers(), byName(name)
```

Level-Formel in `PlayerProfile`: `xpForNextLevel() = 500 + level * 250`; `addXp` trägt Überschuss in Folge-Level.

## Events

Keine. Das Modul definiert keine Event-Typen; Plattform-Events leben in den Plattform-Modulen.

## Commands

Keine.

## Permissions

Keine.

## Konfiguration

Keine. Konfigurationsschema und -laden gehören zu `:shared` (`CoreConfig`, `ConfigLoader`).

## Tests

- `PlayerProfileTest` (6 Tests): Neuanlage über `create`, XP-Formel, einfacher und mehrfacher Level-Aufstieg mit Überschuss, Ablehnung negativer XP, Currency-Zugriff über `balance(Currency)`.

## Erweiterungspunkte

- Neue Feature-Domänen bekommen hier ihr Interface (z. B. später `FriendService`, `PartyService`), Implementierung in `:shared`, Binding in `CoreModule` — Konsumenten holen es über `CoreApi`.
- `EconomyResult.FailureReason` und `NetworkServer.Category` sind bewusst kleine Enums, die pro Phase erweitert werden können, ohne Verträge zu brechen.
- `MessageBus` ist kanalbasiert: neue Cross-Server-Features definieren nur neue Channel-Namen und JSON-Payloads, kein neues Interface.
