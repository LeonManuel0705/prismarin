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
| 3 | Spielserver-Adapter: Status fremder Plugins, Connect- und Queue-API | offen |
| 4 | Speicher: MySQL/MariaDB, SQLite | offen |
| 5 | Admin-System über alle Server | offen, braucht Redis |
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

## Stufe 3: Spielserver-Adapter

- Status eines fremden Spiels ohne Code: in der `servers.yml` einen Platzhalter angeben (z. B. Arena-Status eines Bedwars-Plugins), der Navigator zeigt Wartet/Läuft/Voll.
- Schnittstelle `GameServerState` für eigene Modi, die ihren Zustand selbst melden.
- Connect- und Queue-API: Spieler in eine Gruppe schicken, freien Server wählen, Party später mitnehmen.

## Stufe 4: Speicher

- MySQL/MariaDB als dritter Speicher neben JSON und MongoDB, atomare Buchungen per bedingtem `UPDATE`.
- SQLite für einzelne Server ohne Datenbank.
- Migration zwischen den Speichern per Befehl.

## Stufe 5: Admin-System über alle Server

Alles hier läuft netzwerkweit und setzt Redis voraus, sonst bleibt es auf einem Server.

- Team-Chat (`/sc`) und Teamliste mit Server und Status.
- Spieler finden und bewegen: `/find`, `/send`, `/goto`, `/sendgroup`.
- Strafen: Ban, Mute, Kick, Warn mit Dauer, Grund, Historie und Durchsetzung am Proxy beim Login.
- Reports mit Kategorie, Warteschlange fürs Team und Benachrichtigung.
- Ankündigungen an alle, eine Gruppe oder einen Server, als Chat, Titel oder Bossbar, auch zeitgesteuert.
- Wartung pro Server oder Gruppe statt nur für das ganze Netzwerk, Whitelist für Teammitglieder.
- Befehl auf einer Gruppe ausführen (`/netexec bedwars say ...`), mit Rechteprüfung.
- Spielerakte `/pinfo`: Konto, Währungen, Strafen, zuletzt gesehen, aktueller Server.
- Audit-Log: wer hat wann welche Strafe, Buchung oder Wartung ausgelöst.
- Warnungen bei Ausfall oder niedriger TPS, im Spiel für das Team und als Discord-Webhook.
- Netzwerkweites Vanish.

## Stufe 6: Veröffentlichen

- `:api` als Maven-Paket mit Quellen, über JitPack oder GitHub Packages.
- Release-Jars auf GitHub, Modrinth und Hangar, Versionierung nach SemVer.
- Doku-Seite: Installation, Config-Referenz, Platzhalter, API-Beispiele.
- Beispiel-Plugin, das einen eigenen Modus an PRISMARIN anschließt.

## Stufe 7: Baukasten für Spielmodi

Was früher die Game-Engine für eigene Spiele war, wird ein Werkzeugkasten für andere: Arena-Zustände, Countdown, Map-Vorlagen mit Reset, Zuschauer, Kits, Statistik-Schnittstelle. Optional, ein Netzwerk kann genauso fremde Spiel-Plugins nutzen.

## Gestrichen

MLGRush, Bedwars, Skywars, Murder Mystery, Cosmetics und Season Pass als eigene PRISMARIN-Inhalte. Das sind Dinge, die Netzwerke selbst mitbringen.
