# Modul `:shared`

## Anforderungen

- Implementiert alle `:api`-Verträge ohne Plattform-Kopplung: läuft identisch unter Paper und Velocity.
- Zwei Storage-Profile hinter einem Interface: lokale JSON-Dateien (Standalone) und MongoDB (Netzwerk).
- Zwei Message-Bus-Varianten: In-Process (LOCAL) und Redis Pub/Sub (Cross-Server).
- Locale-Dienst mit Fallback-Kette und Component-Cache; Konfigurationsladen über Configurate.
- Economy-Transaktionen müssen atomar sein, auch bei parallelem Zugriff.

## Architektur

`java-library`-Modul; exportiert `:api`, Configurate, Lettuce und den MongoDB-Treiber als `api`-Abhängigkeiten, Gson und Adventure bleiben `compileOnly` (bündeln beide Plattformen). Fabrikmuster an den Verzweigungspunkten: `MessageBusFactory` wählt anhand `CoreConfig.redisEnabled()`, die Storage-Wahl (`LOCAL`/`MONGO`) trifft `core-paper`s `ProfileStorage`. Alle Services sind konstruktorinjizierbar und halten ihren eigenen Virtual-Thread-Executor, sofern keiner übergeben wird.

## Klassenstruktur

```
net.prismarin.shared
├── config
│   ├── ConfigLoader             lädt config.yml, schreibt Defaults bei Erststart
│   └── CoreConfig               Record + Enum StorageMode LOCAL/MONGO
├── economy
│   └── EconomyServiceImpl       delegiert atomar an ProfileRepository.incrementCurrency
├── locale
│   └── YamlLocaleService        seedet messages_de/en.yml, flacht YAML zu dotted keys,
│                                Fallback-Kette Sprache -> de -> Key, Component-Cache
├── messaging
│   ├── LocalMessageBus          In-Process Pub/Sub (CopyOnWriteArrayList je Channel)
│   ├── MessageBusFactory        create(CoreConfig) -> Redis oder Local
│   └── RedisMessageBus          Lettuce Pub/Sub, Gson-Envelope, getrennte Pub/Sub-Connections
├── profile
│   └── ProfileServiceImpl       ConcurrentHashMap-Cache, load/save/unload/modify auf Virtual Threads
└── storage
    ├── ProfileRepository        find / upsert / incrementCurrency(uuid, currency, delta, allowNegative)
    ├── JsonFileProfileRepository  profiles/<uuid>.json, 64 Lock-Stripes, atomarer Temp-File-Move
    └── MongoProfileRepository     Collection "profiles", findOneAndUpdate mit $gte-Filter gegen Negativsaldo
```

## Events

Keine. `MessageBus`-Handler werden auf beliebigen Threads aufgerufen (Redis-Dispatch-Thread bzw. Publisher-Thread bei LOCAL) — Konsumenten müssen selbst auf den Main-Thread wechseln.

## Commands

Keine.

## Permissions

Keine.

## Konfiguration

`ConfigLoader` liest `config.yml` (wird mit diesen Defaults erzeugt, falls nicht vorhanden):

| Key | Default | Bedeutung |
| --- | --- | --- |
| `server-id` | `unknown` | Eindeutige Server-Kennung, Quelle in `NetworkMessage.sourceServer` |
| `storage.mode` | `LOCAL` | `LOCAL` (JSON-Dateien) oder `MONGO` |
| `storage.mongo.uri` | `mongodb://localhost:27017` | Mongo-Verbindung |
| `storage.mongo.database` | `prismarin` | Mongo-Datenbankname |
| `redis.enabled` | `false` | `true` schaltet auf `RedisMessageBus` |
| `redis.host` | `localhost` | Redis-Host |
| `redis.port` | `6379` | Redis-Port |
| `redis.password` | `""` | Optionales Passwort |
| `locale.default` | `de` | Default-Locale neuer Profile und für Konsolen-Sender |

Ressourcen: `messages_de.yml` / `messages_en.yml` als Seed-Vorlagen für `YamlLocaleService` (identische Key-Sets; im Core-Paper-Jar überschreiben die umfangreicheren Core-Dateien diese Seeds).

## Tests

Unit (Standard-`test`-Task):

- `EconomyServiceImplTest` (6): Balance-Lesen, Deposit/Withdraw-Delegation, `INSUFFICIENT_FUNDS`, Ablehnung nicht-positiver Beträge.
- `YamlLocaleServiceTest` (6): Seeding, dotted-Key-Flattening, Fallback Sprache→de→Key, Platzhalter-Resolver.
- `LocalMessageBusTest` (4): Publish/Subscribe, Envelope-Felder, Channel-Isolation, close.
- `JsonFileProfileRepositoryTest` (7): Roundtrip, Upsert, atomare Inkremente, Negativsaldo-Schutz, fehlendes Profil.

Integration (`@Tag("integration")`, separater `integrationTest`-Task, nur CI — Docker ist lokal nicht verfügbar):

- `RedisMessageBusIT` (3, Testcontainers Redis): Cross-Instanz-Pub/Sub.
- `MongoProfileRepositoryIT` (5, Testcontainers MongoDB): Persistenz und atomare Currency-Updates.

## Erweiterungspunkte

- Neues Storage-Backend = neue `ProfileRepository`-Implementierung plus Zweig in `core-paper`s `ProfileStorage` und ggf. `CoreConfig.StorageMode`.
- Neue Cross-Server-Features nutzen `MessageBus` mit eigenem Channel-Namen und Gson-Payload — keine Bus-Änderung nötig.
- Weitere Sprachen: `BUNDLED_LANGUAGES` in `YamlLocaleService` erweitern und `messages_<lang>.yml` beilegen.
- `ProfileServiceImpl`/`EconomyServiceImpl` akzeptieren einen externen Executor für Tests und geteilte Pools.
