# Modul `:api`

## Anforderungen

- Plattformneutrale Verträge für alle Netzwerk-Dienste: kein Paper, kein Velocity, kein Guice, keine Treiber.
- Einzige externe Abhängigkeit ist Adventure (`adventure-api`, `adventure-text-minimessage`) als `compileOnly`, weil beide Plattformen es bündeln.
- Alle I/O-Verträge sind asynchron (`CompletableFuture`); Implementierungen liegen in `:shared`.
- Diese API ist der Vertrag für fremde Plugins. Sie darf sich nur mit einer neuen Minor-Version brechen, solange die Version unter 1.0 liegt.

## Einstieg für fremde Plugins

```java
Prismarin prismarin = PrismarinProvider.get();
Currency coins = prismarin.economy().primaryCurrency();
prismarin.economy().deposit(player.getUniqueId(), coins, 50);
prismarin.events().subscribe(BalanceChangedEvent.class, event -> log(event.newBalance()));
```

Auf Paper liegt die Instanz zusätzlich im ServicesManager (`Bukkit.getServicesManager().load(Prismarin.class)`). Das Plugin braucht `depend: [PrismarinCore]` in der `plugin.yml`, auf Velocity eine Abhängigkeit auf `prismarin-core`.

## Klassenstruktur

```
net.prismarin.api
├── Prismarin                serverId, branding, profiles, economy, locale, messageBus, servers, serverStatus, events,
│                            enabledSystems(), systemEnabled(name)
├── PrismarinSystems         ECONOMY, LEVELS, NETWORK_STATUS
├── PrismarinProvider        get(), isAvailable(); register/unregister nur durch die Core-Plugins
├── brand
│   └── Branding             Record (name, primaryColor, accentColor, prefix), DEFAULT, gradient(), title()
├── economy
│   ├── Currency             Record (id), COINS und GEMS als Standard, of(id), messageKey()
│   ├── EconomyResult        Record (success, newBalance, failureReason), UNKNOWN_CURRENCY, SYSTEM_DISABLED
│   ├── EconomyService       enabled(), currencies(), primaryCurrency(), currency(id), balance/deposit/withdraw/set atomar
│   └── BalanceChangedEvent  nach jeder erfolgreichen Transaktion (DEPOSIT, WITHDRAW, SET)
├── event
│   ├── EventBus             subscribe(type, handler) -> Subscription, publish(event)
│   ├── PrismarinEvent       Marker
│   └── Subscription         unsubscribe()
├── locale
│   └── LocaleService        render(key, locale, resolvers), raw(key, locale), format(text, resolvers)
├── messaging
│   ├── MessageBus           publish/subscribe/close/serverId, JSON-Payloads
│   └── NetworkMessage       Record-Envelope
├── profile
│   ├── PlayerProfile        uuid, name, firstJoin, lastSeen, locale, balances je Currency, level, xp, settings
│   ├── ProfileService       load/getCached/save/unload/modify
│   ├── ProfileLoadedEvent   nach dem Laden (created = Erstjoin)
│   └── ProfileUnloadedEvent nach Speichern und Entladen
└── server
    ├── NetworkServer        Record (name, displayName, host, port, group, properties), GROUP_LOBBY, GROUP_DEFAULT
    ├── ServerDirectory      servers(), byName(name), byGroup(group)
    ├── ServerStatus         online, players, maxPlayers, latencyMillis, version, prismarin, tps, mspt, RAM, uptime
    └── ServerStatusService  statuses(), status(name), refresh()
```

## Abgeschaltete Systeme

Ist `economy` aus, liefert `economy()` eine Attrappe: `enabled()` ist `false`, `currencies()` leer, `primaryCurrency()` wirft `IllegalStateException`, Buchungen scheitern mit `SYSTEM_DISABLED`. Plugins prüfen vorher `systemEnabled(PrismarinSystems.ECONOMY)`.

## Events

Laufen synchron auf dem Thread, der sie auslöst; für Profile und Economy ist das ein I/O-Thread. Vor Bukkit- oder Velocity-Zugriffen auf den Main-Thread wechseln. Ein Handler, der wirft, hält die anderen nicht auf. Events gelten nur für den Server, auf dem die Änderung passiert ist.

## Tests

`PlayerProfileTest`, `CurrencyTest`, `BrandingTest`.
