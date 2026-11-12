# Modul `:shared`

## Anforderungen

- Implementiert alle `:api`-Verträge ohne Plattform-Kopplung: läuft identisch unter Paper und Velocity.
- Vier Storage-Modi hinter denselben Interfaces (`ProfileRepository`, `DocumentStore`): lokale JSON-Dateien und SQLite (Standalone), MongoDB und MySQL/MariaDB (Netzwerk).
- Zwei Message-Bus-Varianten: In-Process (LOCAL) und Redis Pub/Sub (Cross-Server).
- Locale-Dienst mit Fallback-Kette und Component-Cache; Konfigurationsladen über Configurate.
- Economy-Transaktionen müssen atomar sein, auch bei parallelem Zugriff.

## Architektur

`java-library`-Modul; exportiert `:api`, Configurate, Lettuce, den MongoDB-Treiber, HikariCP, MariaDB Connector/J und sqlite-jdbc als `api`-Abhängigkeiten, Gson und Adventure bleiben `compileOnly` (bündeln beide Plattformen). Fabrikmuster an den Verzweigungspunkten: `MessageBusFactory` wählt anhand `CoreConfig.redisEnabled()`, die Storage-Wahl (`LOCAL`/`MONGO`/`MYSQL`/`SQLITE`) trifft `ProfileStorage.create`. Alle Services sind konstruktorinjizierbar und halten ihren eigenen Virtual-Thread-Executor, sofern keiner übergeben wird.

## Klassenstruktur

```
net.prismarin.shared
├── config
│   ├── ConfigLoader             lädt config.yml, schreibt Defaults bei Erststart
│   ├── CoreConfig               Record + Enum StorageMode LOCAL/MONGO/MYSQL/SQLITE
│   └── SqlSettings              Record für storage.mysql.* und storage.sqlite.file
├── economy
│   └── EconomyServiceImpl       delegiert atomar an ProfileRepository.incrementCurrency
├── locale
│   └── YamlLocaleService        seedet messages_de/en.yml, flacht YAML zu dotted keys,
│                                Fallback-Kette Sprache -> de -> Key, Component-Cache
├── admin
│   ├── AuditLog                 Collection "audit", record (fire and forget) und store (Future)
│   └── LocalAuditService        AuditService über AuditLog, prüft Aktion, Akteur und Längen
├── messaging
│   ├── LocalMessageBus          In-Process Pub/Sub (CopyOnWriteArrayList je Channel)
│   ├── MessageBusFactory        create(CoreConfig) -> Redis oder Local
│   └── RedisMessageBus          Lettuce Pub/Sub, Gson-Envelope, getrennte Pub/Sub-Connections
├── network                    Aufrufe Spielserver -> Proxy (siehe unten)
│   ├── NetworkCalls             Drahtformat network-request/-answer, Aktionen, 30 000-Byte-Grenze, within(...)
│   ├── ApiJson                  Gson für API-Records (Duration als Millisekunden), Feldleser mit klaren Fehlern
│   ├── ProxyLink                call(action, body, avoid); orNoRoute(...) als Rückfall
│   ├── Route                    Träger wählen: anderer Spieler, dann Redis, dann der gemiedene Spieler
│   ├── PendingCalls             offene Aufrufe pro Route, Zeitlimit, Abbruch beim Verlassen des Trägers
│   ├── RemoteDispatcher         Proxy-Seite: Aktion -> API-Dienst -> Antwort, NoticeSink mit Herkunftsserver
│   ├── RemoteAudit, RemoteMaintenance, RemoteNotifications, RemoteReports, RemoteTemplates
│   │                            Backend-Seite der API-Dienste über einen ProxyLink
│   ├── ModerationCatalog        Zwischenspeicher für Gründe, Vorlagen, Claim-Zeit; sendet eigene Einträge erneut
│   ├── NetworkEvents            ReportFiledEvent als network-event an die Spielserver
│   └── NoRouteException, RemoteCallException
├── profile
│   └── ProfileServiceImpl       ConcurrentHashMap-Cache, load/save/unload/modify auf Virtual Threads
└── storage
    ├── ProfileStorage           wählt Repository + DocumentStore nach storage.mode, schließt Client/Pool
    ├── ProfileRepository        find / upsert / incrementCurrency(uuid, currency, delta, allowNegative)
    ├── JsonFileProfileRepository  profiles/<uuid>.json, 64 Lock-Stripes, atomarer Temp-File-Move
    ├── MongoProfileRepository     Collection "profiles", findOneAndUpdate mit $gte-Filter gegen Negativsaldo
    ├── SqlProfileRepository       Tabellen prismarin_profiles + prismarin_balances, Zeilensperre pro Spieler
    ├── DocumentStore            save / find / findAll / entries / findBy(feld, wert) / delete / collections
    ├── PluginDataStorage        DataStorage der API: Namensräume fremder Plugins auf dem DocumentStore
    ├── PluginCollections        physischer Name ext_<namensraum>_<collection>, owned(), in(store)
    ├── JsonFileDocumentStore      documents/<collection>/<hex(id)>.json
    ├── MongoDocumentStore         Collection "documents_<collection>", Rohtext in body, Felder als k/v-Paare
    ├── SqlDocumentStore           Tabelle prismarin_documents, findBy filtert in Java mit LIKE-Vorfilter
    ├── SqlDatabase                HikariCP-Pool, Dialekt, Transaktionen mit Wiederholung bei Deadlocks
    └── StorageException           ungeprüfte Hülle um SQLException
```

## Storage

| `storage.mode` | Profile | Dokumente | Geteilt zwischen Servern |
| --- | --- | --- | --- |
| `LOCAL` | `JsonFileProfileRepository` | `JsonFileDocumentStore` | nein |
| `SQLITE` | `SqlProfileRepository` | `SqlDocumentStore` | nein, eine Datei pro Server |
| `MONGO` | `MongoProfileRepository` | `MongoDocumentStore` | ja |
| `MYSQL` (Alias `MARIADB`) | `SqlProfileRepository` | `SqlDocumentStore` | ja |

Alle Implementierungen halten dieselbe Semantik. Die SQL-Klassen und alle `DocumentStore`-Varianten laufen gegen die gemeinsamen Vertragstests `ProfileRepositoryContract` und `DocumentStoreContract`, JSON- und Mongo-Profile behalten ihre eigenen Tests mit denselben Fällen:

- `save` schreibt Salden nur beim ersten Anlegen eines Profils; danach ändern ausschließlich `incrementCurrency` und `setCurrency` Salden.
- `incrementCurrency` ist in der Datenbank atomar, lehnt bei `allowNegative = false` jeden Unterlauf mit `INSUFFICIENT_FUNDS` und dem aktuellen Saldo ab und sättigt bei Überlauf an `Long.MAX_VALUE` (JSON und SQL auch an `Long.MIN_VALUE`).
- Ohne Profil liefern `incrementCurrency` und `setCurrency` `PROFILE_NOT_LOADED` und legen nichts an.
- Collection-Namen folgen `[a-z0-9_-]{1,64}`, Dokument-IDs sind 1 bis 128 Zeichen lang, exakt und groß/klein-sensitiv. `findBy` vergleicht ein Feld der obersten Ebene, dessen primitiver Wert als String gleich dem gesuchten Wert ist; `findAll` und `findBy` sortieren nach den UTF-8-Bytes der ID.

`collections()` nennt jede Collection mit mindestens einem Dokument, sortiert (JSON: Unterordner von `documents/`, SQL: `SELECT DISTINCT collection`, Mongo: Collections mit Präfix `documents_`). `StorageMigration` kopiert neben den festen Core-Collections alle Collections mit Präfix `ext_`, also die Daten fremder Plugins aus `Prismarin.storage()`.

### Speicher für fremde Plugins

`PluginDataStorage` legt jede Collection eines Plugins unter `ext_<namensraum>_<collection>` ab. Namensräume folgen `[a-z0-9][a-z0-9-]{1,23}` ohne Unterstrich, Collections `[a-z0-9][a-z0-9-]{0,31}`, `prismarin` ist reserviert. Weil keine eigene Collection mit `ext_` beginnt und der Namensraum keinen Unterstrich enthalten darf, kann kein Paar zwei Plugins auf denselben Namen abbilden. Alle Zugriffe laufen auf dem Executor des Cores, ungültige Namen und IDs werfen sofort `IllegalArgumentException`.

SQL-Schema (wird beim Start mit `CREATE TABLE IF NOT EXISTS` angelegt):

| Tabelle | Spalten | Schlüssel |
| --- | --- | --- |
| `prismarin_profiles` | `uuid`, `name`, `first_join`, `last_seen` (Epoch-Millis), `locale`, `level`, `xp`, `settings` (JSON-Text) | `uuid` |
| `prismarin_balances` | `uuid`, `currency`, `balance` (BIGINT) | `(uuid, currency)` |
| `prismarin_documents` | `collection`, `id` (UTF-8-Bytes als VARBINARY/BLOB), `body` (Rohtext) | `(collection, id)` |

Dialekte: MySQL/MariaDB nutzt InnoDB mit `utf8mb4`, `READ COMMITTED`, `SELECT ... FOR UPDATE` auf die Profilzeile als Sperre pro Spieler und `INSERT ... ON DUPLICATE KEY UPDATE`. SQLite läuft mit einer einzigen Pool-Verbindung, WAL-Journal, `synchronous=NORMAL` und `BEGIN IMMEDIATE`, Upserts über `ON CONFLICT ... DO UPDATE`. Deadlocks (SQLState `40`) und Schlüsselkonflikte bei gleichzeitigem Erstanlegen (`23`) werden bis zu dreimal wiederholt. `findBy` holt bei Werten aus `[A-Za-z0-9._:-]` per `LIKE` nur passende Kandidaten und filtert dann exakt in Java; andere Werte lesen die ganze Collection.

Treiber: MySQL und MariaDB laufen beide über MariaDB Connector/J (`jdbc:mariadb://host:port/database`), der auch MySQL-Server ab 5.7 spricht; ein `jdbc:mysql:`-URL oder `permitMysqlScheme` ist nicht nötig, weil die URL intern gebaut wird. Connector/J 4 prüft TLS standardmäßig (`sslMode=verify-full`). Gegen einen MySQL-Server mit selbstsigniertem Zertifikat `sslMode: trust` unter `storage.mysql.properties` setzen, gegen einen Server ohne TLS `sslMode: disable` (bei MySQL 8 mit `caching_sha2_password` zusätzlich `allowPublicKeyRetrieval: true`). Die Datenbank selbst muss existieren, die Tabellen legt das Plugin an.

## Aufrufe vom Spielserver zum Proxy

Die Pakete `network` und `admin` tragen die API-Dienste, die auf dem Proxy leben, auf die Spielserver: Audit-Log, Wartung, Team-Meldungen, Rundrufe, Reports und Strafvorlagen. Ein Spielserver schickt `network-request` (`id`, `action`, `body`) über `prismarin:moderation` mit einem Spieler als Träger oder, wenn niemand online ist und Redis läuft, über den Bus-Kanal `prismarin:network`. Der Proxy antwortet mit `network-answer` (`id`, `ok`, `error` oder `body`) über denselben Träger bzw. über `prismarin:network-answer` mit `to` = Absender.

| Aktion | Body | Antwort |
| --- | --- | --- |
| `audit-record`, `audit-latest` | `actor`, `action`, `target`, `details` bzw. `target`, `limit` | `{}` bzw. `entries`, `truncated` |
| `maintenance-state`, `maintenance-set` | `{}` bzw. `kind` (`network`, `server`, `group`), `name`, `active`, `actor` | `state` bzw. `changed` |
| `staff-alert`, `staff-chat`, `broadcast` | `alert` (`StaffAlert`), `chat` (`StaffChat`), `broadcast` (`Broadcast`) | `delivery` |
| `moderation-catalog` | `{}` | `reports`, `templates`, `reasons`, `claim-millis`, `template-list` |
| `report-file`, `report-open`, `report-find`, `report-claim`, `report-release`, `report-close` | `reporter`, `reporter-name`, `target`, `reason`, `id`, `staff`, `verdict` | `submission`, `reports`, `report` |
| `reason-register`, `reason-unregister`, `template-register`, `template-unregister` | `reason`, `template` oder `id` | `{}` |
| `template-preview`, `template-punish` | `target`, `template`, `actor`, `note` | `preview` bzw. `punishment` |
| `alts-find` | `player` | `alts` (`AltAccount`s), `truncated` |

Jede Nachricht bleibt unter 30 000 Bytes, Listen werden mit `truncated` gekürzt. Records werden mit `ApiJson` geschrieben und beim Lesen von ihren eigenen Konstruktoren geprüft; Fehler gehen als lesbarer Text zurück und werden beim Aufrufer zu `RemoteCallException`. Ohne Weg zum Proxy wirft der Link `NoRouteException`: Meldungen liefern dann `Delivery.NO_ROUTE`, Audit-Einträge bleiben lokal.

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
| `storage.mode` | `LOCAL` | `LOCAL` (JSON-Dateien), `SQLITE`, `MONGO` oder `MYSQL` (Alias `MARIADB`), Groß/Kleinschreibung egal |
| `storage.mongo.uri` | `mongodb://localhost:27017` | Mongo-Verbindung |
| `storage.mongo.database` | `prismarin` | Mongo-Datenbankname |
| `storage.mysql.host` | `localhost` | MySQL/MariaDB-Host |
| `storage.mysql.port` | `3306` | MySQL/MariaDB-Port |
| `storage.mysql.database` | `prismarin` | Datenbankname, muss existieren |
| `storage.mysql.username` | `prismarin` | Benutzer |
| `storage.mysql.password` | `""` | Passwort, in Anführungszeichen setzen |
| `storage.mysql.pool-size` | `8` | Maximale Verbindungen pro Server im HikariCP-Pool |
| `storage.mysql.properties` | `{}` | Zusätzliche Treiber-Optionen von Connector/J, z. B. `sslMode: trust` |
| `storage.sqlite.file` | `prismarin.db` | SQLite-Datei, relativ zum Plugin-Ordner oder absolut |
| `redis.enabled` | `false` | `true` schaltet auf `RedisMessageBus` |
| `redis.host` | `localhost` | Redis-Host |
| `redis.port` | `6379` | Redis-Port |
| `redis.password` | `""` | Optionales Passwort |
| `locale.default` | `de` | Default-Locale neuer Profile und für Konsolen-Sender |

Ressourcen: `messages_de.yml` / `messages_en.yml` als Seed-Vorlagen für `YamlLocaleService` (identische Key-Sets; im Core-Paper-Jar überschreiben die umfangreicheren Core-Dateien diese Seeds).

## Tests

Unit (Standard-`test`-Task):

- `ProfileLevelServiceTest` (10): XP, Level setzen und verschieben, Grenzen, Offline-Profile, Reihenfolge von `XpGainedEvent` und `LevelChangedEvent`.
- `EconomyServiceImplTest` (6): Balance-Lesen, Deposit/Withdraw-Delegation, `INSUFFICIENT_FUNDS`, Ablehnung nicht-positiver Beträge.
- `YamlLocaleServiceTest` (6): Seeding, dotted-Key-Flattening, Fallback Sprache→de→Key, Platzhalter-Resolver.
- `LocalMessageBusTest` (4): Publish/Subscribe, Envelope-Felder, Channel-Isolation, close.
- `JsonFileProfileRepositoryTest` (7): Roundtrip, Upsert, atomare Inkremente, Negativsaldo-Schutz, fehlendes Profil.
- `SqliteProfileRepositoryTest` (19): `ProfileRepositoryContract` gegen SQLite plus Wiederöffnen der Datei und idempotentes Schema.
- `JsonFileDocumentStoreTest` (16) und `SqliteDocumentStoreTest` (16): `DocumentStoreContract` (Roundtrip, Ersetzen, Sortierung, `findBy`-Regeln, Sonderzeichen, exakte IDs, Validierung) plus je zwei speicherspezifische Fälle.
- `ProfileStorageTest` (2): Auswahl der Implementierungen und eine gemeinsame SQLite-Datei für Profile und Dokumente.
- `PluginDataStorageTest` (6): physische Namen passen in jeden Speicher und treffen nie Core-Collections, Namensräume sind getrennt, Listen und `findBy` roh und typisiert, Validierung vor jedem Zugriff, Migration nimmt `ext_`-Collections mit.
- `NetworkCallsTest` (6), `PendingCallsTest` (5), `RouteTest` (3), `NetworkEventsTest` (3): Drahtformat und Ablehnung kaputter Nachrichten, Größengrenze, Routen, Zeitlimit, Ereignisweitergabe.
- `RemoteRoundTripTest` (14): jeder Dienst einmal über JSON vom Spielserver zum `RemoteDispatcher` und zurück, mit Rückfall ohne Route, abgeschalteten Modulen, abgelehnten Registrierungen, Heilung nach Proxy-Neustart und gekürzten Listen.

Integration (`@Tag("integration")`, separater `integrationTest`-Task, nur CI — Docker ist lokal nicht verfügbar):

- `RedisMessageBusIT` (3, Testcontainers Redis): Cross-Instanz-Pub/Sub.
- `MongoProfileRepositoryIT` (5, Testcontainers MongoDB): Persistenz und atomare Currency-Updates.
- `MongoDocumentStoreIT` (14, Testcontainers MongoDB): `DocumentStoreContract`.
- `MariaDbStorageIT` (31, Testcontainers MariaDB 11.4) und `MySqlStorageIT` (31, Testcontainers MySQL 8.4 über den MariaDB-Treiber): beide Verträge als `@Nested`-Klassen gegen einen Container.

## Erweiterungspunkte

- Neues Storage-Backend = neue `ProfileRepository`- und `DocumentStore`-Implementierung plus Zweig in `ProfileStorage.create` und `CoreConfig.StorageMode`; die Vertragstests `ProfileRepositoryContract` und `DocumentStoreContract` werden einfach erweitert.
- Weitere SQL-Datenbanken brauchen einen neuen `SqlDialect` (Typen, Tabellenoptionen, Upsert, Sperrklausel) und eine Fabrikmethode in `SqlDatabase`.
- Neue Cross-Server-Features nutzen `MessageBus` mit eigenem Channel-Namen und Gson-Payload — keine Bus-Änderung nötig.
- Weitere Sprachen: `BUNDLED_LANGUAGES` in `YamlLocaleService` erweitern und `messages_<lang>.yml` beilegen.
- `ProfileServiceImpl`/`EconomyServiceImpl` akzeptieren einen externen Executor für Tests und geteilte Pools.

## Chat filter engine

Package `net.prismarin.shared.chatfilter`, used by the Paper filter and the proxy filter:

| Class | Job |
| --- | --- |
| `ChatFilterSettings` | record of `chat-filter.yml` with `load(file, proxy)`, defaults, comments (`YamlComments`), problems for wrong values; `policy(rule)` for built-in and plugin rules |
| `ChatFilterEngine` | runs `flood`, `repeat`, `words`, `links`, `caps` and plugin rules; `check(text)` without state, `filter(message, bypassed)` with flood and repeat state per player and channel; `register`, `unregister`, `rules`, `forget` |
| `TextNormalizer` | folding (case, NFKD accents, `ß`, look-alikes), canonical words with leetspeak, trimming, simplified text for repeats |
| `WordMatcher` | words with wildcards and phrases, repeat tolerant patterns, joined single letters, regular expressions, allowed words; returns `Span`s in the original text |
| `LinkMatcher` | domains with a known ending or a scheme, IPv4, obfuscated dots mapped back to the original text, allowed domains with subdomains |
| `CapsCheck`, `ChatState` | share of capitals and softening; flood window and repeat history with edit distance |
| `Span` | start, end, detail; `merge`, `replace` |
| `ViolationCounter` | escalation counting per player and rule with a sliding window, returns the trigger with the last 20 evidence lines and starts again |
| `ChatViolation` | the `chat-violation` wire message between backend and proxy, `of(message, result, settings)`, `toJson`, `parse` with validation |

Tests: `TextNormalizerTest`, `WordMatcherTest` (leetspeak, repeated letters, separators, phrases, wildcards, Scunthorpe, Niger, normal capital words), `LinkMatcherTest`, `ChatStateTest`, `ChatFilterEngineTest`, `ViolationCounterTest`, `ChatFilterSettingsTest`, `ChatViolationTest`.

## Alt account storage

`moderation/AltStore` keeps the collections `alt-accounts` and `alt-addresses` (see [`core-velocity.md`](core-velocity.md#alt-accounts)) with striped locks per document, bounded lists and a retention; `prune()` removes expired entries. `moderation/DocumentAltService` implements `AltService` on top of it and marks bans and mutes through `PunishmentService`. `network/RemoteAlts` is the backend side of the network call `alts-find`.
