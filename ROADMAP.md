# PRISMARIN — Roadmap

Geordnete Phasen vom heutigen Stand bis zum vollen Netzwerk. Jede Phase baut auf den vorherigen auf; Abhängigkeiten stehen explizit dabei. Größenangaben sind grobe Schätzungen in zusammenhängenden Bau-Sessions (S = wenige Abende, M = 1–2 Wochen nebenbei, L = mehrere Wochen, XL = Monatsprojekt).

## Phasenübersicht

| Phase | Thema | Hängt ab von | Größe |
| --- | --- | --- | --- |
| 0 | Fundament + Lobby | — | erledigt |
| 1 | Social-Basis | 0 + Redis | M |
| 2 | Game-Engine | 0 | L |
| 3 | MLGRush | 2 | M |
| 4 | Bedwars | 2, 3 | L |
| 5 | Skywars + Murder Mystery | 2, 4 | L |
| 6 | Progression | 2–5, 1 | L |
| 7 | Cosmetics & Identität | 6 | L |
| 8 | Moderation & Staff-Tools | 1 | M–L |
| 9 | Web & Discord | 1, 6 | L |
| 10 | Skalierung & Betrieb | 2, 9 | XL |

## Phase 0 — Fundament + Lobby (heute, erledigt)

**Ziele:** Tragfähige Basis, auf der jedes weitere Feature nur noch andockt.

**Gebaut:**
- Monorepo mit `:api` / `:shared` / `:core-paper` / `:core-velocity` / `:lobby-paper`, Convention-Plugin, CI mit Unit- und Integrationstests (Testcontainers).
- Profile (JSON lokal / MongoDB), Economy (Coins/Gems, atomar), Locale-System (de/en, MiniMessage), MessageBus (lokal / Redis), Serververzeichnis.
- Paper-Core: Chat-Format mit Rang-Prefix (LuckPerms optional), Tablist mit animiertem Titel, Sidebar-Framework, Menü-Framework, `/coins`, `/economy`.
- Velocity-Core: MOTD, Wartungsmodus, `/hub`, `/sendall`, Lobby-Fallback bei Kicks.
- Lobby: Spawn/Schutz/Void-Teleport, Hotbar-Items, Navigator- und Profil-Menü, Spieler-Verstecker, Double-Jump, animierte Sidebar, Build-Modus, `/setspawn`.
- Infra-Stack (Docker Compose: MongoDB, Redis, MariaDB, Prometheus, Grafana) als Ziel-Setup; Live-Betrieb heute LOCAL auf Windows.

## Phase 1 — Social-Basis (Redis-Pflicht)

**Ziele:** Das Netzwerk fühlt sich wie EIN Server an. Ab hier ist `storage.mode: MONGO` + `redis.enabled: true` auf allen Servern Betriebsvoraussetzung — alle Features dieser Phase sind Cross-Server.

**Module (Wunschliste):**
- Cross-Server-Chat (globale Kanäle, Staff-Chat) über `MessageBus`-Channels.
- Private Messages (`/msg`, `/r`) mit Offline-/Anderer-Server-Zustellung.
- Friends (Anfragen, Liste, Online-Status, Join-Benachrichtigung).
- Party (Einladen, Leader, gemeinsamer Serverwechsel — Grundlage für Team-Spiele in P3/P4).
- JoinMe (klickbare Einladung in den eigenen Server/Spielmodus).

**Technische Voraussetzungen:** Redis-Stack aus `infra/` produktiv; neue `:api`-Interfaces (`FriendService`, `PartyService`, ...) mit Mongo-Repositories in `:shared`; Presence-Tracking (wer ist auf welchem Server) als Redis-State; Velocity als Teilnehmer am selben Bus (bereits verdrahtet).

**Größe:** M — viel Payload-/Channel-Design, wenig UI.

## Phase 2 — Game-Engine

**Ziele:** Wiederverwendbarer Spielrahmen, Voraussetzung für ALLE Spiele (P3–P5). Kein Spielmodus schreibt Rundenlogik doppelt.

**Module (Wunschliste):**
- Arena- und GameManager (Zustandsmaschine: Lobby → Countdown → Ingame → Endphase → Reset).
- Countdown-System mit Titeln/Sounds/BossBar.
- MapLoader (Map-Templates aus Vorlagenordner, Map-Voting-fähig).
- World-Reset (Template-Kopie oder Slime-artiges Zurücksetzen nach jeder Runde).
- Spectator-Modus (Tod → Zuschauer, Teleport-Kompass).
- Kit-System (Auswahl, Speicherung im Profil).

**Technische Voraussetzungen:** nur Phase 0; sauberes `:game-engine`-Modul (Paper) nach dem `lobby-paper`-Muster (`compileOnly` auf Core, Child-Injector über `CoreApi`); Stats-Schnittstelle pro Spiel schon mitdenken (P6).

**Größe:** L — wichtigste Einzelinvestition der Roadmap.

## Phase 3 — Erstes Spiel: MLGRush

**Ziele:** Kleinster sinnvoller Scope, um die Engine unter Realbedingungen zu härten: 1v1, ein Bett, Best-of-Runden.

**Module (Wunschliste):**
- MLGRush-Modul auf der Game-Engine (1v1-Arenen, Best-of-3/5).
- ELO-/Ranked-Wertung pro Spieler (Mongo), Anzeige in Sidebar/Tablist.
- Rematch-Flow und schnelle Requeue.

**Technische Voraussetzungen:** Phase 2 komplett; Party (P1) für „gegen Freund spielen"; einfache Queue (1v1-Matcher) — die große Matchmaking-Infrastruktur kommt erst in P10.

**Größe:** M.

## Phase 4 — Bedwars

**Ziele:** Das Flaggschiff-Spiel. Teams, Wirtschaft im Spiel, private Runden.

**Module (Wunschliste):**
- Teams (2er/4er, Betten, Team-Chat, Team-Auswahl).
- Shop (Item-Shop an Villagern/NPCs, Team-Upgrades).
- Generatoren (Eisen/Gold/Diamant/Smaragd, Stufen, Splitting).
- Private Games mit Owner-Rechten (Freunde einladen, Regeln togglen, Start erzwingen).

**Technische Voraussetzungen:** Phase 2 (Engine) und Erfahrungen aus Phase 3; Party-Integration (P1) für Team-Queues; Map-Pool mit Team-Spawn-Metadaten im MapLoader.

**Größe:** L.

## Phase 5 — Skywars + Murder Mystery

**Ziele:** Portfolio verbreitern, Engine-Generalisierung beweisen (Solo-FFA und Rollen-Spiel).

**Module (Wunschliste):**
- Skywars (Solo/Team, Insel-Maps, Chest-Loot-Tiers, Events wie Border/Refill).
- Murder Mystery (Rollen Mörder/Detektiv/Unschuldige, Rollen-Sandboxing, Gold-Spawns).

**Technische Voraussetzungen:** Phase 2 + 4 (Loot-Tabellen, Rollen-System als Engine-Erweiterungen); Spectator und World-Reset müssen bis hier stabil sein.

**Größe:** L (zwei mittlere Spiele).

## Phase 6 — Progression

**Ziele:** Langzeitmotivation. Die Level-Anzeige existiert bereits (Profil hat `level`/`xp`, Sidebar + Menü zeigen sie) — jetzt bekommt sie Substanz.

**Module (Wunschliste):**
- Achievements (pro Spiel + netzwerkweit, Fortschritt im Profil).
- Quests (täglich/wöchentlich, XP- und Coin-Belohnungen).
- Daily Rewards (Streak-System).
- Season Pass (kostenloser Track; kaufbarer Track erst mit Payment, siehe unten).
- Leaderboards (pro Spiel und global, Mongo-Aggregation, Anzeige als Hologramm/Menü/Web).

**Technische Voraussetzungen:** Spiele aus P3–P5 liefern Stats-Events; XP-Vergabe zentral über `ProfileService.modify`/eigenen `ProgressionService`; Redis für Live-Leaderboard-Caches (P1).

**Größe:** L — viele kleine Systeme mit gemeinsamem Datenmodell.

## Phase 7 — Cosmetics & Identität

**Ziele:** Ausdruck und (später) Monetarisierungs-Grundlage — alles rein kosmetisch.

**Module (Wunschliste):**
- Cosmetics (Partikel-Effekte, Trails, Kill-Effekte, Win-Dances).
- Nick-System (Anzeigename/Skin-Maskierung mit Rechte-Konzept).
- Crates (Schlüssel als Belohnung, Öffnungs-Animation, Duplikat-Handling).
- Pets und Gadgets in der Lobby.

**Technische Voraussetzungen:** Progression (P6) als Belohnungsquelle; Inventar-/Besitz-Modell im Profil (`settings` reicht nicht mehr — eigene Collections); Lobby-Hotbar um Cosmetics-Item erweitern.

**Größe:** L — inhaltsgetrieben, gut parallelisierbar.

## Phase 8 — Moderation, Reports & Staff-Tools

**Ziele:** Das Netzwerk sicher betreibbar machen, bevor es öffentlich wächst.

**Module (Wunschliste):**
- Punishments (Ban/Mute/Kick mit Dauer, Historie, Velocity-Enforcement beim Login).
- Reports (Ingame-Report mit Kategorie, Staff-Queue, Benachrichtigung über MessageBus).
- Staff-Tools (Vanish, Teleport, Inspect, Chat-Logs, Freeze).
- Replay/Spectator-Plus (Runden-Aufzeichnung bzw. erweitertes Nachträglich-Zuschauen für Report-Prüfung).

**Technische Voraussetzungen:** Phase 1 (Cross-Server-Benachrichtigung, Presence); Punishment-Store in Mongo mit Cache am Proxy; LuckPerms-Gruppen für Staff-Ränge.

**Größe:** M–L (Replay ist der L-Anteil und kann nachgezogen werden).

## Phase 9 — Web & Discord

**Ziele:** Das Netzwerk nach außen sichtbar machen.

**Module (Wunschliste):**
- REST API (Stats, Leaderboards, Spielerprofile, Serverstatus — lesend zuerst).
- WebSocket (Live-Daten: Online-Zahlen, laufende Runden).
- Discord-Bot (Verknüpfung Minecraft↔Discord, Report-/Punishment-Benachrichtigungen, Stats-Abfragen).
- Website (Landing, Stats-Seiten, Regelwerk).

**Technische Voraussetzungen:** Phase 1 (Redis als Datenquelle für Live-Zustand) und Phase 6 (es gibt etwas anzuzeigen); eigenes Deployment außerhalb der Minecraft-Prozesse; Auth-Konzept (API-Tokens, später OAuth für Spieler-Login).

**Größe:** L — separates Tech-Profil (Web statt Paper), gut abkoppelbar.

## Phase 10 — Skalierung & Betrieb

**Ziele:** Vom handverwalteten Windows-Host zum elastischen Netzwerk.

**Module (Wunschliste):**
- Docker-Deployment der Gameserver (Images für Paper-Backends, dynamisches Hoch-/Runterfahren von Spielservern).
- Matchmaking/Queue (zentrale Queue am Proxy/eigenem Dienst, Server-Zuteilung, Warteschlangen-UI).
- Monitoring-Ausbau (Server-Metriken aus den Plugins nach Prometheus, Alerting, Dashboards je Spielmodus).
- Cloudflare (DNS, Proxy/DDoS-Schutz fürs Web, ggf. Spectrum für Minecraft-Traffic).

**Technische Voraussetzungen:** Spiele + Engine stabil (P2–P5), REST/Status-Daten (P9); `ServerDirectory` von statischer YAML auf dynamische Registrierung (Redis) umstellen; Infra-Stack aus `infra/` wird Produktionsbasis.

**Größe:** XL — eher Betriebs- als Feature-Arbeit, bewusst zuletzt.

## Payments & kaufbare Ränge (ehrliche Einordnung)

- **Was die Mojang/Minecraft-EULA erlaubt:** verkauft werden dürfen kosmetische Vorteile (Cosmetics, Pets, Partikel, Nick-Farben) und Ränge, solange sie keinen spielerischen Vorteil geben — kein Pay-to-win, keine kaufbaren Items/Kits/Boosts, die Runden entscheiden. Zugangs-Vorteile (z. B. volle Lobbys betreten) bewegen sich in einer Grauzone und müssen einzeln geprüft werden.
- **Was rechtlich dranhängt:** echtes Payment heißt Gewerbeanmeldung, Steuern, Impressum, Widerrufsrecht — und Zahlungsanbieter wie Stripe/PayPal/Tebex setzen Volljährigkeit bzw. eine verantwortliche erwachsene Person voraus. Der Owner ist 16: ein rechtssicherer Shop ist damit **jetzt nicht umsetzbar**, auch nicht „inoffiziell".
- **Konsequenz für die Roadmap:** Konzept jetzt, Integration später. Phasen 6 und 7 bauen alles so, dass Monetarisierung nur ein Freischalt-Flag ist: Ränge, Cosmetics-Besitz, Season-Pass-Tracks und Crate-Keys existieren als Datenmodell und sind über Progression verdienbar. Wenn die Voraussetzungen erfüllt sind (Volljährigkeit oder Gewerbe über Erziehungsberechtigte), kommt ein Shop (z. B. Tebex) als dünne Schicht obendrauf — ohne Umbau der Spielsysteme, und strikt EULA-konform (nur Kosmetik + faire Ränge).
