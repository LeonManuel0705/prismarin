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
├── messaging
│   ├── LocalMessageBus          In-Process Pub/Sub (CopyOnWriteArrayList je Channel)
│   ├── MessageBusFactory        create(CoreConfig) -> Redis oder Local
│   └── RedisMessageBus          Lettuce Pub/Sub, Gson-Envelope, getrennte Pub/Sub-Connections
├── profile
│   └── ProfileServiceImpl       ConcurrentHashMap-Cache, load/save/unload/modify auf Virtual Threads
└── storage
    ├── ProfileStorage           wählt Repository + DocumentStore nach storage.mode, schließt Client/Pool
    ├── ProfileRepository        find / upsert / incrementCurrency(uuid, currency, delta, allowNegative)
    ├── JsonFileProfileRepository  profiles/<uuid>.json, 64 Lock-Stripes, atomarer Temp-File-Move
    ├── MongoProfileRepository     Collection "profiles", findOneAndUpdate mit $gte-Filter gegen Negativsaldo
    ├── SqlProfileRepository       Tabellen prismarin_profiles + prismarin_balances, Zeilensperre pro Spieler
    ├── DocumentStore            save / find / findAll / findBy(feld, wert) / delete für JSON-Dokumente
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

SQL-Schema (wird beim Start mit `CREATE TABLE IF NOT EXISTS` angelegt):

| Tabelle | Spalten | Schlüssel |
| --- | --- | --- |
| `prismarin_profiles` | `uuid`, `name`, `first_join`, `last_seen` (Epoch-Millis), `locale`, `level`, `xp`, `settings` (JSON-Text) | `uuid` |
| `prismarin_balances` | `uuid`, `currency`, `balance` (BIGINT) | `(uuid, currency)` |
| `prismarin_documents` | `collection`, `id` (UTF-8-Bytes als VARBINARY/BLOB), `body` (Rohtext) | `(collection, id)` |

Dialekte: MySQL/MariaDB nutzt InnoDB mit `utf8mb4`, `READ COMMITTED`, `SELECT ... FOR UPDATE` auf die Profilzeile als Sperre pro Spieler und `INSERT ... ON DUPLICATE KEY UPDATE`. SQLite läuft mit einer einzigen Pool-Verbindung, WAL-Journal, `synchronous=NORMAL` und `BEGIN IMMEDIATE`, Upserts über `ON CONFLICT ... DO UPDATE`. Deadlocks (SQLState `40`) und Schlüsselkonflikte bei gleichzeitigem Erstanlegen (`23`) werden bis zu dreimal wiederholt. `findBy` holt bei Werten aus `[A-Za-z0-9._:-]` per `LIKE` nur passende Kandidaten und filtert dann exakt in Java; andere Werte lesen die ganze Collection.

Treiber: MySQL und MariaDB laufen beide über MariaDB Connector/J (`jdbc:mariadb://host:port/database`), der auch MySQL-Server ab 5.7 spricht; ein `jdbc:mysql:`-URL oder `permitMysqlScheme` ist nicht nötig, weil die URL intern gebaut wird. Connector/J 4 prüft TLS standardmäßig (`sslMode=verify-full`). Gegen einen MySQL-Server mit selbstsigniertem Zertifikat `sslMode: trust` unter `storage.mysql.properties` setzen, gegen einen Server ohne TLS `sslMode: disable` (bei MySQL 8 mit `caching_sha2_password` zusätzlich `allowPublicKeyRetrieval: true`). Die Datenbank selbst muss existieren, die Tabellen legt das Plugin an.

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

- `EconomyServiceImplTest` (6): Balance-Lesen, Deposit/Withdraw-Delegation, `INSUFFICIENT_FUNDS`, Ablehnung nicht-positiver Beträge.
- `YamlLocaleServiceTest` (6): Seeding, dotted-Key-Flattening, Fallback Sprache→de→Key, Platzhalter-Resolver.
- `LocalMessageBusTest` (4): Publish/Subscribe, Envelope-Felder, Channel-Isolation, close.
- `JsonFileProfileRepositoryTest` (7): Roundtrip, Upsert, atomare Inkremente, Negativsaldo-Schutz, fehlendes Profil.
- `SqliteProfileRepositoryTest` (19): `ProfileRepositoryContract` gegen SQLite plus Wiederöffnen der Datei und idempotentes Schema.
- `JsonFileDocumentStoreTest` (16) und `SqliteDocumentStoreTest` (16): `DocumentStoreContract` (Roundtrip, Ersetzen, Sortierung, `findBy`-Regeln, Sonderzeichen, exakte IDs, Validierung) plus je zwei speicherspezifische Fälle.
- `ProfileStorageTest` (2): Auswahl der Implementierungen und eine gemeinsame SQLite-Datei für Profile und Dokumente.

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
