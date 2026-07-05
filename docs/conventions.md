# PRISMARIN — Code-Konventionen

Verbindlich für jedes Modul in diesem Repo. Abweichungen gelten als Review-Fehler.

## Build & Toolchain

- Gradle Kotlin DSL, Convention-Plugin `prismarin.java` aus `build-logic` in jedem Modul: `options.release = 21`, UTF-8, JUnit-Platform, `-XX:+EnableDynamicAgentLoading` für Mockito auf neuen JDKs.
- Abhängigkeiten ausschließlich über den Version-Catalog `gradle/libs.versions.toml`; keine Versionsliterale in Modul-Buildfiles (Ausnahme: das Shadow-Plugin in `core-velocity`).
- Repositories: `mavenCentral()` + `https://repo.papermc.io/repository/maven-public/`.
- Plattform-gebündelte Bibliotheken (Paper-API, Velocity-API, Adventure, Gson, LuckPerms) sind immer `compileOnly`; in Tests mit expliziter Version als `testImplementation`.
- Integrationstests tragen `@Tag("integration")` und laufen nur im separaten `integrationTest`-Task. Lokal niemals `integrationTest` ausführen — Docker ist lokal nicht verfügbar, die Testcontainer-Tests sind CI-only (`.github/workflows/ci.yml`).

## Keine Kommentare

- Keine Inline-Kommentare, keine TODOs, keine Platzhalter, keine leeren Methoden, kein auskommentierter Code. Code erklärt sich über Namen und kleine Methoden.
- Einzige Ausnahme: JavaDoc auf öffentlichen Interfaces/Klassen des Moduls `:api`. Nirgendwo sonst.

## Locale & Texte

- Jeder für Spieler sichtbare String liegt in Locale-Dateien, niemals im Java-Code. `messages_de.yml` ist die Default-Sprache, `messages_en.yml` der vollständige Fallback — beide Dateien haben immer identische Key-Sets.
- Format ist MiniMessage. Aufgelöst wird über `LocaleService.render(key, locale, resolvers)`; Platzhalter mit `Placeholder.unparsed` (Rohtext) bzw. `Placeholder.component` (bereits gerenderte Components).
- Fallback-Kette zur Laufzeit (`YamlLocaleService`): Spieler-Sprache → `de` → Key selbst.
- Paper-Module ohne eigenen LocaleService (wie `lobby-paper`) legen ihre Keys in den Locale-Dateien von `core-paper` ab, mit Modul-Prefix (`lobby.*`).
- Spieler-Locale immer über den Profil-Cache bevorzugen (`PlayerLocales.of(player, profiles)`), Konsole/Fallback über `locale.default` aus der Config.

## Brand

- Name `PRISMARIN`; Farben PRIMARY `#22D3EE`, ACCENT `#2DD4BF`, BG_DARK `#0F172A`, GOLD `#FBBF24`, Fließtext `gray` — Konstanten aus `net.prismarin.api.brand.Brand`, nie hart kodiert.
- Chat-Prefix exakt: `<gradient:#22D3EE:#2DD4BF><b>PRISMARIN</b></gradient> <dark_gray>|</dark_gray> <gray>` — ein Netzwerk, ein Prefix; Subsysteme erfinden keine eigenen.
- Keine Emojis. Einfache Unicode-Glyphen wie `◆ ✔ ✖ ●` sind erlaubt.

## Async-Regeln (Paper)

- **Niemals I/O auf dem Main-Thread**: Datenbank-, Datei- und Netzwerkzugriffe laufen als `CompletableFuture` auf dem Virtual-Thread-Executor (`Executors.newVirtualThreadPerTaskExecutor()`, als Singleton `ExecutorService` im Core-Injector gebunden).
- Bukkit-API aus async Threads nicht anfassen, außer dokumentiert-sicheren Aufrufen (Adventure-`sendMessage`, Scheduler). Nach abgeschlossenen Futures mit `Bukkit.getScheduler().runTask(plugin, ...)` zurück auf den Main-Thread hüpfen — Muster siehe `EconomyCommand.handleTransfer` und `LobbyMenus.switchLocale`.
- `AsyncPlayerPreLoginEvent` ist die einzige Stelle, an der blockierend auf ein Future gewartet wird (Auth-Thread, Timeout, sonst Kick). Gameplay-Code liest ausschließlich den synchronen Profil-Cache (`ProfileService.getCached`).
- Shutdown-Pfade warten mit Timeout auf offene Saves (`PrismarinCorePlugin.flushProfiles`), danach Ressourcen in umgekehrter Startreihenfolge schließen.
- `MessageBus`-Handler kommen auf beliebigen Threads an — vor jedem Bukkit-Zugriff Thread-Hop.

## Modul-Muster: neues Paper-Modul auf PrismarinCore

1. **Modul anlegen**: `include(":<name>-paper")` in `settings.gradle.kts`; `build.gradle.kts` nach dem Vorbild von `lobby-paper`:

```kotlin
plugins {
    id("prismarin.java")
}

dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":shared"))
    compileOnly(project(":core-paper"))
    compileOnly(libs.paper.api)
    compileOnly(libs.guice)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}
```

2. **`plugin.yml`**: `depend: [PrismarinCore]`, `api-version: "1.21"`, `version: "${version}"`, Commands und Permissions deklarieren. Keine `libraries:`-Einträge — der Core ist der Library-Host, alle Klassen von `api`/`shared`/`core-paper` kommen zur Laufzeit über den Plugin-Classloader des Cores.
3. **Bootstrap**: im `onEnable` Child-Injector bauen und alles darüber beziehen:

```java
injector = CoreApi.get().injector().createChildInjector(new MeinModul(this));
```

Eigene Services im `AbstractModule` binden; Core-Services (`ProfileService`, `LocaleService`, `EconomyService`, `MenuService`, `SidebarManager`, `MessageBus`, `ExecutorService`, `MiniMessage`) werden automatisch vom Parent aufgelöst.
4. **Aufräumen**: `onDisable` in umgekehrter Reihenfolge — registrierte Provider (z. B. `SidebarManager.provider(null)`) und Channels abmelden, Referenzen nullen. Kein Schließen von Core-Ressourcen, die gehören dem Core.
5. **Texte**: alle Keys mit Modul-Prefix in `core-paper/src/main/resources/messages_de.yml` **und** `messages_en.yml` ergänzen (Key-Sets müssen identisch bleiben).
6. **Distribution**: neuen `from(...)`-Block im `dist`-Task des Root-Builds ergänzen, damit das Jar unter `dist/` landet.

## Sonstiges

- Konfigurationsdateien via Configurate YAML; beim Erststart Defaults schreiben bzw. Ressourcen-Seed kopieren (Muster: `ConfigLoader`, `YamlServerDirectory`, `LocationsFile`).
- Records für unveränderliche Daten, `sealed interface` für Parse-/Ergebnistypen (Muster: `EconomyCommandParser.Result`).
- Parse- und Berechnungslogik von Bukkit trennen, damit sie unit-testbar bleibt (`EconomyCommandParser`, `TeamSortKeys`, `XpProgressBar`, `MenuLayout`).
- Tests: JUnit 5 + Mockito; jede neue reine Logikklasse bekommt Tests, Bukkit-gebundene Klassen bleiben dünn.
