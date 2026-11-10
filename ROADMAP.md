# PRISMARIN Roadmap

PRISMARIN ist ein Framework für Minecraft-Netzwerke auf Paper und Velocity. Ein Netzwerk installiert die Jars, hängt seine eigenen Spielserver dahinter (ein vorhandenes Bedwars-Plugin genauso wie einen selbst geschriebenen Modus) und bekommt Profile, Währungen, Sprachen, Serververwaltung und Admin-Werkzeuge über alle Server hinweg. Eigene Spielmodi baut PRISMARIN nicht mehr; die Lobby bleibt ein optionales Modul.

Grundregeln für alles, was hier entsteht:

- Kompatibel sein heißt, die Standards zu bedienen, die andere Plugins schon nutzen (Vault, PlaceholderAPI, LuckPerms), statt zu verlangen, dass sie PRISMARIN einbauen.
- Jedes Modul ist abschaltbar, damit PRISMARIN nie mit TAB, EssentialsX und Co. kämpft.
- Texte verstehen MiniMessage, `&`-Farbcodes und Hex (`&#RRGGBB`). Platzhalter schreiben wir als `%name%`.
- Plattformen: Paper (mit Purpur, Pufferfish) und Velocity. Folia und BungeeCord erst, wenn jemand danach fragt.
- Lizenz MIT.

## Übersicht

| Stufe | Thema | Stand |
| --- | --- | --- |
| 1 | Entkoppeln: Marke, Währungen, Servergruppen, öffentliche API, Events, Module | fertig (0.2.0) |
| 1b | Netzwerkübersicht `/network` mit Ping und Heartbeat | fertig (0.2.0) |
| 1c | Ganze Systeme abschaltbar, z. B. das Coin-System | fertig (0.2.0) |
| 2 | Brücken: Vault, PlaceholderAPI, Konflikterkennung | fertig (0.2.0) |
| 3 | Spielserver-Adapter: Status fremder Plugins, Connect- und Queue-API | fertig (0.3.0) |
| 4 | Speicher: MySQL/MariaDB, SQLite | fertig (0.3.0) |
| 5 | Admin-System über alle Server | fertig (0.3.0, Ergänzungen 0.4.0), das meiste ohne Redis |
| 6 | Veröffentlichen: Maven-Paket, Releases, Doku, Beispiel-Plugin | offen |
| 7 | Baukasten für Spielmodi (ehemals Game-Engine) | offen |

## Stufe 1: Entkoppeln (fertig)

- `Branding` statt fester `Brand`-Konstanten, Abschnitt `branding` in der `config.yml`, Tags `%brand%` und `%brand_prefix%`.
- Währungen frei wählbar (`economy.currencies`, `economy.primary`), Kontostände als Map je Währung. Alte JSON-Profile mit `coins`/`gems` werden beim ersten Lesen übernommen.
- Server haben eine freie Gruppe und beliebige Eigenschaften (`icon`, `description` usw.) statt der festen Kategorien LOBBY/GAME/BUILD. Alte `category`-Einträge werden umgedeutet.
- Öffentliche API: `PrismarinProvider.get()`, auf Paper auch im ServicesManager. Gleiche Schnittstelle auf Proxy und Backends.
- Events: `ProfileLoadedEvent`, `ProfileUnloadedEvent`, `BalanceChangedEvent`.
- Module abschaltbar: Paper `chat`, `tablist`, `sidebar`, `join-messages`, `economy-commands`, `network-menu`; Velocity `motd`, `maintenance`, `hub-command`, `sendall-command`, `lobby-fallback`.
- Fehlende Config-Abschnitte werden beim Start ergänzt, fehlende Sprach-Keys kommen aus dem Jar.

## Stufe 1b: Netzwerkübersicht (fertig)

- `/network` (Alias `/servers`, Recht `prismarin.admin.network`) öffnet ein Inventar mit allen Servern aus der `servers.yml`: Status, Spieler/Max, Ping, Version, Gruppe; Klick verbindet.
- Daten kommen aus zwei Quellen: Server-List-Ping (geht auch bei Servern ohne PRISMARIN) und ein Heartbeat alle 5 s über den Message-Bus mit TPS, MSPT, RAM und Laufzeit.
- Im LOCAL-Modus erreicht der Heartbeat nur den eigenen Server. Für TPS und RAM aller Server braucht es Redis.

## Stufe 1c: Ganze Systeme abschaltbar (fertig)

- Neuer Abschnitt `systems` in der `config.yml`: `economy`, `levels`, `network-status`. Module (`modules`) schalten einzelne Anzeigen und Befehle, Systeme ganze Funktionen.
- `economy: false`: keine Währungen, kein `/coins` und `/economy`, keine Coin-Anzeigen in Lobby und Sidebar. `EconomyService.enabled()` meldet `false`, Buchungen scheitern mit `SYSTEM_DISABLED`.
- `levels: false`: keine Level- und XP-Anzeigen.
- `network-status: false`: kein Heartbeat, kein regelmäßiger Ping, kein `/network`.
- Abhängige Module gehen mit aus und das Log nennt den Grund.
- Fremde Plugins fragen `Prismarin.systemEnabled("economy")` oder `enabledSystems()`.
- Lobby mit eigener `config.yml` unter `features`: `navigator`, `profile-menu`, `player-hider`, `double-jump`, `protection`, `sidebar`, `join-title`.
- Die Lobby zeigt die konfigurierten Währungen (bis zu drei) statt fest Coins und Gems. Eigene Texte pro Währung über `lobby.sidebar.<id>`, sonst ein allgemeiner Text.

## Stufe 2: Brücken (fertig)

- Vault: PRISMARIN meldet sich beim Laden (`onLoad`) mit hoher Priorität als Economy-Anbieter für die Primärwährung, damit auch Plugins, die vor Prismarin starten, es finden. Beträge werden auf ganze Einheiten gerundet, Banken gibt es nicht. Schalter `modules.vault`, aus bei `systems.economy: false`.
- PlaceholderAPI-Erweiterung `%prismarin_…%`: `balance`, `balance_<währung>`, `level`, `xp`, `xp_required`, `locale`, `brand`, `server`, `network_online`, `network_servers_online`, `server_online_<server>`, `server_max_<server>`, `server_status_<server>`, `server_ping_<server>`, `group_online_<gruppe>`.
- Umgekehrt lösen Tablist, Sidebar, Lobby-Sidebar und Chat-Format fremde PAPI-Platzhalter auf. Eigene Platzhalter wie `%player%` werden vorher ersetzt und gehen nie an PAPI.
- Konflikterkennung: TAB schaltet die Tablist ab, FeatherBoard/AnimatedScoreboard/SimpleScore die Sidebar, EssentialsChat/ChatControl/VentureChat/LPC/DeluxeChat/CarbonChat/ChatManager das Chat-Format. Abschalten mit `compatibility.auto-detect: false`.
- Sprache einheitlich: selbst gewählte Sprache aus dem Profil, sonst die des Clients.
- Live geprüft (08.11.2026) mit VaultUnlocked 2.20.3 und PlaceholderAPI 2.12.3: Vault meldet „Economy Legacy: PRISMARIN“, `%vaultunlocked_eco_balance%` liefert denselben Stand wie `%prismarin_balance%`, alle `%prismarin_…%`-Platzhalter lösen auf, PAPI-Platzhalter im eigenen Chat-Format auch.
- Zusätzlich die neue VaultUnlocked-API (`net.milkbowl.vault2`, „Economy Modern“) mit allen konfigurierten Währungen, `set`, `canWithdraw` und `canDeposit`. Wird nur registriert, wenn VaultUnlocked installiert ist; mit dem klassischen Vault bleibt es bei der alten API.
- Live mit einem Test-Plugin geprüft, das wie ein Shop über Vault bucht: Einzahlen, Abbuchen, zu wenig Guthaben, Bruchteile, unbekannte Währung, Setzen, über beide Schnittstellen. Danach stimmten die Stände in PRISMARIN.

## Stufe 3: Spielserver-Adapter (fertig)

- Spielzustände `WAITING`, `STARTING`, `RUNNING`, `ENDING`, `RESTARTING`, dazu „voll“, wenn die Spielerzahl das Maximum erreicht.
- Ein Spielserver meldet seinen Zustand über `prismarin.gameState().state(...)` oder ohne Code über `game-state.placeholder` in seiner `config.yml` (ein PAPI-Platzhalter des Spiel-Plugins) mit einstellbaren Wörtern unter `game-state.mapping`.
- Der Zustand geht per Heartbeat raus und steht zusätzlich in der Server-List-MOTD des Backends (`game-state.write-motd`). So sehen Lobbys ihn auch ohne Redis. Fremde Server ohne PRISMARIN werden über Wörter wie „Waiting“ oder „Ingame“ in ihrer MOTD erkannt.
- `servers.yml` kennt jetzt `groups:` mit Anzeigename, Icon und Beschreibung. Der Navigator zeigt pro Gruppe einen Eintrag mit Spielerzahl und freien Servern, ein Klick wählt den besten freien Server (zuerst „startet“, dann der vollste wartende).
- `prismarin.connector()`: `connect`, `pick(group)`, `connectToGroup`, auf Paper und Velocity. Dazu `/play <gruppe>` und die Platzhalter `server_state_<server>` und `group_joinable_<gruppe>`.

## Stufe 4: Speicher (fertig)

- MySQL/MariaDB und SQLite neben JSON und MongoDB, atomare Buchungen in der Datenbank.
- Allgemeiner Dokumentenspeicher für Strafen, Reports, Audit-Log und Namen, in allen vier Speicherarten.
- Umzug zwischen den Speichern: `/prismarin migrate <von> <nach>` auf dem Spielserver und `/prismarinproxy migrate` auf dem Proxy, aus der Konsole oder per RCON. Kopiert Profile, Kontostände, Namen, Strafen, Reports, Audit-Log und Wartungsziele (0.4.0).

## Stufe 5: Admin-System (fertig)

Fast alles läuft im Proxy-Plugin und braucht deshalb kein Redis. Jedes Werkzeug hat einen eigenen Schalter unter `modules`.

- Team-Chat `/sc`, Teamliste `/team`, `/find`, `/goto`, `/send <spieler|all|server:x> <server|group:x>`.
- Strafen: `/ban`, `/mute` (mit Dauer wie `30m`, `12h`, `7d`, `1w`), `/kick`, `/warn`, `/unban`, `/unmute`, `/history`. Bans greifen beim Login am Proxy, Mutes setzt der Spielserver durch, der Proxy meldet sie ihm über den Kanal `prismarin:moderation`. Als API: `prismarin.punishments()` mit Events.
- Reports: `/report` mit Abklingzeit, `/reports` und `/reports close <id>`, Meldung an das Team und optional an Discord.
- Ankündigungen: `/alert [-t] [server:x|group:x] Nachricht`, als Chat und auf Wunsch als Titel.
- Wartung pro Server oder Gruppe: `/maintenance server|group <name> on|off`, Bypass mit `prismarin.maintenance.bypass`.
- Spielerakte `/pinfo`, Audit-Log `/audit [ziel]`.
- Warnungen bei Ausfall und niedriger TPS (TPS nur mit Redis) im Spiel und per Discord-Webhook aus `admin.yml`.
- `/netexec <all|server|group:x> <befehl>` über Redis.
- Vanish wie SuperVanish: `/vanish`, Sichtbarkeit über `prismarin.vanish.see` und Stufen `prismarin.vanish.level.<n>`, weg aus Tab, Serverliste und Spielerzahlen, kein Aufsammeln, keine Mobs, keine Druckplatten und Sculk-Sensoren, kein Schlafen-Zählen, keine Fortschritts-Meldungen, Zustand bleibt beim Serverwechsel.
- Lautlose Behälter (0.4.0): Unsichtbare öffnen Truhen, Fässer und Shulker als Kopie ohne Deckel und Geräusch, Änderungen werden beim Schließen übernommen, wenn sich der Behälter inzwischen nicht verändert hat. Lautloser Abbau ohne Partikel und Geräusch. Beides unter `vanish.silent-containers` und `vanish.silent-blocks` abschaltbar. Offen: die Riss-Animation beim Abbauen und das Platzier-Geräusch sehen und hören andere weiterhin.
- OPs vom Spielserver auf dem Proxy (0.4.0): Mit `permissions.trust-backend-operators: true` in der `admin.yml` des Proxys bekommen Spieler, die auf ihrem aktuellen Spielserver OP sind, alle `prismarin.*`-Rechte auf dem Proxy. Gedacht für kleine Netzwerke ohne LuckPerms auf dem Proxy, standardmäßig aus.
- `/prismarin` und `/prismarinproxy` zeigen Version, Speicher, Bus, Systeme, Module und warum ein Modul aus ist.

## Stufe 6: Veröffentlichen

- `:api` als Maven-Paket mit Quellen, über JitPack oder GitHub Packages.
- Release-Jars auf GitHub, Modrinth und Hangar, Versionierung nach SemVer.
- Doku-Seite: Installation, Config-Referenz, Platzhalter, API-Beispiele.
- Beispiel-Plugin, das einen eigenen Modus an PRISMARIN anschließt.

## Stufe 7: Baukasten für Spielmodi

Was früher die Game-Engine für eigene Spiele war, wird ein Werkzeugkasten für andere: Arena-Zustände, Countdown, Map-Vorlagen mit Reset, Zuschauer, Kits, Statistik-Schnittstelle. Optional, ein Netzwerk kann genauso fremde Spiel-Plugins nutzen.

## Gestrichen

MLGRush, Bedwars, Skywars, Murder Mystery, Cosmetics und Season Pass als eigene PRISMARIN-Inhalte. Das sind Dinge, die Netzwerke selbst mitbringen.
