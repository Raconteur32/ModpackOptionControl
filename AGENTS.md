# AGENTS.md — Travailler sur MOC

> MOC (Modpack Option Control) : mod Fabric pour **packmakers** — gérer les options de config d'un modpack par patches ordonnés au niveau option (DEFAULT/OVERRIDE), jamais par fichiers entiers.

## Modules

| Module | Rôle |
|---|---|
| `common` | Moteur partagé : `MocFileSystem`, content types (json/properties/toml/text), diff option-level, patches, migration. Dépend de rien d'autre. |
| `fabric` | Le mod runtime (applique les patches au lancement du jeu). |
| `gui` | GUI desktop (Compose). Consomme `common` ; a sa **propre copie** de `DraftPatch`/`RecompositionDraft` (miroir de `gui_web` — toute modif de finalisation doit être faite des deux côtés). |
| `gui_web` | Serveur Ktor (REST + WebSocket) + SPA vanilla JS sans build step (`src/main/resources/static`). Même état disque que la GUI desktop, interchangeable. |

Java 25, Gradle Kotlin DSL, Kotlin 2.3.21.

## Commandes

```bash
./gradlew :common:test :gui_web:test   # tests unitaires (gui n'a pas de tests — NO-SOURCE)
cd gui_web && npx vitest run           # tests JS (peu nombreux)
./gradlew :gui_web:shadowJar           # jar autonome gui_web/build/libs/moc-web.jar
./web-gui-run.sh [/chemin/instance]    # build + run
./gui-run.sh                           # GUI desktop
```

## Tester gui_web

### Tests automatisés (JUnit + ktor-server-test-host)

- Hériter de `WebTestBase` : helpers `gameFile(name, content)` / `refFile(name, content)` (instance + ref jetables dans un tempdir via `TestPlatformService`), bloc `webTest { client -> ... }`.
- **`forkEvery = 1`** dans le build : les singletons (`DraftPatch`, `RecompositionDraft`, `IgnoreStore`, filesystems) gardent leur état entre classes — chaque classe de test tourne dans sa JVM. Ne pas le retirer.
- **`McInstanceMocFileSystem.reload()` après chaque écriture de fichier** dans un test : le scan n'a lieu qu'au chargement.
- Gson sérialise les payloads en échappant `'` → `` : pour asserter des chemins d'options (`$['a']`), **parser le JSON**, ne pas string-matcher.

### Lancer le serveur manuellement

```bash
MOC_NO_BROWSER=true MOC_PORT=7599 \
  java -Dmoc.gameDir=/chemin/instance -jar gui_web/build/libs/moc-web.jar
```

- Une instance valide = un dossier contenant `config/` **et** `mods/`. Une instance factice vide suffit (`mkdir -p fake/{config,mods}`).
- Sans `moc.gameDir`, le serveur sonde `.`, `..`, `run`, `../fabric/run`, `../run` — attention en le lançant depuis le repo, il peut servir une vraie instance de dev.
- Le port par défaut est **7421** (`MOC_PORT` pour changer). Avant d'en tuer un, vérifier à qui il appartient (`ss -tlnp | grep <port>`).
- `ETag`/fingerprint : la ref de dev n'est régénérée au démarrage que si patches/liste/ignores/version ont changé.

### Pièges de test exploratoire (appris en session)

1. **Pas de rescan à chaud** : le filesystem live n'est scanné qu'au démarrage (sauf add/remove de directory-ignore). Après avoir modifié un fichier sur disque → **redémarrer le serveur**.
2. **Le typage de contenu est épinglé** dans `mocfsmetas/mocmetadata.json` à la première lecture ; un contenu devenu invalide sous son type épinglé bascule en lecture texte brute à `$` (fallback non persisté, auto-guérissant) — cf. spec `common` « Flattened option model ».
3. **Draft et session persistent** sur disque (`config/moc/dev/`) — un redémarrage serveur ne réinitialise pas l'état de staging.
4. Un fichier `.txt` contenant du texte libre est inféré `properties`, pas `text` ; `text` n'emporte que les contenus que rien d'autre ne valide.

### Piloter un navigateur (Playwright)

Pas de MCP Playwright configuré ; pattern utilisé avec succès : petit script Node dans `/tmp` qui lance Chromium en mode **visible** et expose un `/eval` HTTP pour exécuter du JS dans la page (driver persistant entre les commandes). Points d'attention :

- `viewport: null` dans `newContext` (sinon la fenêtre réelle et le rendu divergent) ; sous Wayland, `--ozone-platform=wayland` évite les bizarreries XWayland.
- `pkill -f` se matche lui-même dans un `zsh -c` : tuer par PID (`ss -tlnp`), jamais par pattern contenant la commande courante.
- Sélecteurs utiles : `[data-select-file="<path>"]` (arbre), `.action-dropdown[data-file][data-path]` (dropdown d'action), `#btn-create-patch`, `#staging-list`, `.dialog-root` pour les popups.

## Conventions

- Specs et changes : **OpenSpec** (`openspec/`, schéma spec-driven). Le behavior contract vit dans `openspec/specs/<module>/spec.md` ; toute évolution passe par un change (proposal → specs → design → tasks → apply → archive). Les deltas MODIFIED reprennent le requirement complet.
- `doc/` contient les docs de design détaillées (flux GUI, content types, versioning).
- Code : Kotlin partout, commentaires en franglais assumé ; pas de framework côté front (DOM + innerHTML, délégation d'événements).
- Chemins de fichiers dans l'API : relatifs à la racine de l'instance (ex. `config/app.json`), URL-encodés (`encodeURIComponent`) dans les routes `{file}`.
