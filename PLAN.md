# PoulpifyAuto — Plan de refonte complète

> Objectif : au lancement (ou à la connexion Android Auto), l'app ouvre automatiquement
> une session **hôte** sur le serveur Poulpify, et donne au conducteur, sur l'écran de la
> voiture, les mêmes possibilités que l'interface hôte du site.

Décisions actées :
- Surface voiture **hybride** : service média (media3) + écrans Car App Library.
- Contrôle de lecture via **Spotify App Remote SDK** sur le téléphone (repli serveur).
- Le serveur Poulpify **peut être modifié**, l'app Vue doit rester fonctionnelle.
- Distribution **sideload perso** (aucune fonctionnalité perdue, cf. §8).

---

## 1. Diagnostic de l'existant

### 1.1 Sur l'écran Android Auto : rien n'existe

`PoulpifySession.onCreateScreen()` retourne `QrCodeScreen` — un `PaneTemplate` avec une
image QR statique. Pas de lecture, pas de file, pas de contrôle. Tout le reste
(player, file, passagers, verrou) vit dans `MainActivity`, donc **sur le téléphone
uniquement**. L'app n'est pas déclarée comme app média (`automotive_app_desc.xml`
ne contient que `<uses name="template"/>`).

### 1.2 Bugs bloquants

| # | Problème | Emplacement |
|---|---|---|
| 1 | `POST /api/host/logout` appelle `clearSpotifySession()` qui **supprime les tokens Spotify du serveur**. Or `PoulpifyMediaService.onDestroy()` appelle `logout()`. Fermer l'app auto casse aussi le site web. | `server/index.js:152`, `PoulpifyMediaService.kt:56` |
| 2 | Mot de passe hôte **en dur dans l'APK** : `LoginRequest("poulpi", force)`. Si `HOST_PASSWORD` en prod ≠ `poulpi`, l'app ne se connecte jamais. | `PoulpifyRepository.kt:128` |
| 3 | `autoLogin(force = true)` à chaque démarrage **vide `activeUsers`, `skipVotes`, `recentJoins`** → éjecte tous les passagers connectés. | `server/index.js:133`, `PoulpifyMediaService.kt:37` |
| 4 | `POST /api/boost` **n'existe plus** côté serveur (supprimé au commit `563be44`). Appel Retrofit mort. | `PoulpifyApi.kt:47` |
| 5 | Timeout serveur de **15 s** : la moindre coupure réseau ou mise en veille ferme la session hôte, et l'app n'a **aucune reconnexion**. | `server/index.js:468` |
| 6 | La **pochette est re-téléchargée chaque seconde** : `playerState` ré-émet un objet neuf à chaque poll et le `collectLatest` relance un `URL.openConnection()`. | `MainActivity.kt:200` |
| 7 | **3 requêtes HTTP séquentielles par seconde** en boucle infinie, avec `HttpLoggingInterceptor.Level.BODY` actif même en release. | `PoulpifyRepository.kt:160` |
| 8 | Tous les `catch` font `Log.e` et rien d'autre : **aucune erreur n'est jamais visible** par l'utilisateur. C'est la raison pour laquelle « rien ne marche » sans qu'on sache pourquoi. | partout |
| 9 | `spotify-app-remote-0.8.0.aar` présent dans `shared/libs/` mais **jamais référencé** dans les dépendances. `spotify-auth` déclaré dans le catalogue, inutilisé. | `shared/build.gradle.kts` |
| 10 | `StatusResponse` ne lit pas `autoDisconnectEnabled` ; `usesCleartextTraffic="true"` inutile ; `take(5)` en dur pour la file ; pas de dépôt git. | divers |

### 1.3 Le manque structurel côté serveur

Il n'existe **aucun endpoint de contrôle de lecture hôte** : pas de play, pause, next,
previous, seek, volume, devices, transfer. Le seul « skip » est le vote démocratique.
Même une app auto parfaite ne pourrait pas mettre en pause en l'état.

---

## 2. Architecture cible

```
                       ┌──────────────────────────┐
                       │  Serveur Poulpify (Node) │
                       │  état session + Web API  │
                       └────┬─────────────────┬───┘
                    SSE +   │                 │   OAuth
                    REST    │                 │   Spotify
        ┌───────────────────┴───┐         ┌───┴────────────┐
        │  SessionCoordinator   │         │  Spotify Cloud │
        │  (source de vérité)   │         └───┬────────────┘
        └───┬──────┬──────┬─────┘             │
            │      │      │      App Remote   │
            │      │      └──────────────► App Spotify (téléphone) ──► audio voiture
            │      │
   ┌────────┴──┐ ┌─┴────────────────┐ ┌──────────────────┐
   │ Car App   │ │ MediaLibrary     │ │ App téléphone    │
   │ Library   │ │ Service (media3) │ │ (Compose)        │
   │ dashboard │ │ browse + lecture │ │ config + diag    │
   └───────────┘ └──────────────────┘ └──────────────────┘
```

**Répartition des responsabilités** — c'est la règle qui évite les incohérences actuelles :

| Fonction | Source |
|---|---|
| Transport (play/pause/next/prev/seek) | **App Remote** (instantané), repli `/api/host/player/*` |
| État de lecture + position | **App Remote** (`subscribeToPlayerState`, poussé, pas de polling) |
| Pochette | **App Remote `ImagesApi`** (bitmap direct, zéro requête HTTP) |
| Ajout à la file | **Serveur `/api/queue`** (préserve « ajouté par », le verrou, la vue des invités) |
| Liste de la file à venir | **Serveur** (App Remote n'expose pas la file) |
| Recherche | **Serveur `/api/search`** (App Remote n'a pas de recherche) |
| Passagers, votes, verrou | **Serveur** (SSE) |

---

## 3. Phase 0 — Correctifs serveur (~½ journée)

Dépôt `Poulpify`, fichier `server/index.js`. **Débloque l'existant avant toute réécriture.**

- **0.1** `POST /api/host/logout` : retirer `clearSpotifySession()`. Créer un
  `POST /api/host/spotify/disconnect` distinct pour purger volontairement les tokens,
  et un bouton « Oublier Spotify » séparé dans `App.vue`. *(corrige le bug n°1)*
- **0.2** `force: true` au login : ne plus vider `activeUsers` / `recentJoins` /
  `skipVotes`, seulement faire tourner `currentHostToken`. *(corrige le n°3)*
- **0.3** Auto-déconnexion : seuil 15 s → 45 s, et ne plus effacer les passagers quand
  l'hôte tombe. L'app auto pose `autoDisconnectEnabled: false` au login. *(corrige le n°5)*
- **0.4** Nouveau bloc contrôle hôte, derrière `verifyHostToken` + `verifySpotifyToken`,
  proxys 1-1 vers `api.spotify.com/v1/me/player/*` (~60 lignes) :
  `PUT /api/host/player/play|pause|seek|shuffle|repeat|volume`,
  `POST /api/host/player/next|previous`, `GET /api/host/devices`, `PUT /api/host/transfer`.
- **0.5** `POST /api/host/skip` : skip immédiat hôte sans vote, remet `skipVotes` à zéro.
- **0.6** `GET /api/events` (SSE) diffusant `{status, player, queue, passengers, votes}`.
  Une seule boucle serveur poll Spotify toutes les 2 s et diffuse — au lieu de N clients
  qui pollent chacun. Les endpoints REST actuels restent intacts pour l'app Vue.
  Bénéfice collatéral : arrête de taper le rate limit Spotify. *(corrige le n°7)*
- **0.7** `/api/queue` : accepter un `hostToken` pour ajouter même file verrouillée,
  et marquer `addedByHost`.
- **0.8** `/api/status` : ajouter `spotifyDeviceActive` (cause n°1 des 404 sur `/api/queue`)
  et `serverVersion`.
- **0.9** Persister `currentHostToken` sur disque à côté de `tokens.json` pour survivre
  à un redéploiement Coolify.

**Vérification** : `curl` chaque endpoint ; lancer puis tuer l'app auto et confirmer que
le site web reste connecté à Spotify.

---

## 4. Phase 1 — Socle Android (1–2 j)

Même dépôt, mais `mobile/` et `shared/` sont vidés. Nouvelle structure :

```
app/            Application, AppContainer (DI manuel), UI Compose
core/model/     Kotlin pur : Track, QueueEntry, Passenger, PlaybackSnapshot, ConnectionState
core/data/      DataStore chiffré : mot de passe hôte, token, profil conducteur, réglages
core/network/   Retrofit + kotlinx.serialization + client SSE, mappers DTO → model
core/spotify/   SpotifyRemoteController : wrapper App Remote, Flow<PlayerState>, ImagesApi
core/session/   SessionCoordinator : LA source de vérité unique
car/            PoulpifyCarAppService + écrans templates
media/          PoulpifyMediaLibraryService + SpotifyProxyPlayer
```

**Dépendances à corriger** : référencer réellement l'AAR
(`implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))`), ajouter
`spotify-auth`, Compose BOM, kotlinx-serialization (remplace Gson), okhttp-sse,
DataStore, security-crypto, Coil 3.

**DI** : pas de Hilt. Un `AppContainer` sur l'`Application` (~30 lignes) suffit pour
trois points d'entrée (Activity, CarAppService, MediaLibraryService) et évite le coût
de compilation du traitement d'annotations. Les `Screen` du Car App Library ne sont pas
des composants Android, ce qui rend Hilt plus pénible qu'utile ici.

**Le cœur — `SessionCoordinator`** :

```kotlin
data class PoulpifyUiState(
    val connection: ConnectionState,   // Disconnected|Authenticating|Connected|Degraded|Reconnecting
    val remote: RemoteState,           // NotInstalled|Disconnected|Connected|Unauthorized
    val nowPlaying: NowPlaying?,       // fusion App Remote (temps réel) + serveur (addedBy, isInked)
    val queue: List<QueueEntry>,
    val passengers: List<Passenger>,
    val votes: Votes,
    val queueLocked: Boolean,
    val lastError: UserFacingError?,
)
val state: StateFlow<PoulpifyUiState>
```

Règles non négociables :
- **Un seul état, un seul flux.** Voiture, service média et téléphone en sont trois vues.
- Toute action retourne un `Result<Unit>` ; toute erreur devient un `CarToast` ou un
  snackbar. Plus aucun `catch { Log.e }` muet. *(corrige le n°8)*
- Auto-login au démarrage : mot de passe lu dans DataStore → `/api/host/login` → token
  persisté ; sur 403, re-login silencieux ; backoff exponentiel 1 s → 30 s.
- `ConnectivityManager.NetworkCallback` pour reconnecter dès le retour du réseau.

---

## 5. Phase 2 — Spotify App Remote (1 j + config dashboard)

`SpotifyRemoteController` :
- `ConnectionParams(BuildConfig.SPOTIFY_CLIENT_ID, "poulpifyauto://callback", showAuthView = true)`
- `playerState: Flow<PlayerState>` via `subscribeToPlayerState()` dans un `callbackFlow`
- `imagesApi.getImage(uri, Dimension.LARGE)` pour la pochette *(corrige le n°6)*
- Reconnexion automatique sur `onFailure` / déconnexion
- Traitement **explicite** de `CouldNotFindSpotifyApp`, `NotLoggedInException`,
  `UserNotAuthorizedException` → message clair sur le téléphone **et** sur l'écran voiture

**Prérequis one-shot, sans quoi App Remote refuse de se connecter** — dans le
[dashboard Spotify](https://developer.spotify.com/dashboard) :
- ajouter le package `fr.maxboudier.poulpifyauto`
- ajouter les empreintes **SHA-1 debug ET release**
- ajouter la redirect URI `poulpifyauto://callback`
- scopes `app-remote-control` et `streaming`

Le `clientId` passe par `local.properties` → `BuildConfig`, jamais en dur. *(corrige le n°2)*

---

## 6. Phase 3 — Service média : la surface voiture principale (2 j)

`PoulpifyMediaLibraryService : MediaLibraryService`.

**`SpotifyProxyPlayer : SimpleBasePlayer`** (androidx.media3.common) — l'outil exact pour
ce cas : `getState()` construit depuis `PoulpifyUiState`, `handleSetPlayWhenReady` →
App Remote resume/pause, `handleSeekTo` / `SeekToNext` / `SeekToPrevious` → App Remote.
**Ne demande jamais l'audio focus** : c'est une télécommande, Spotify produit le son.

**Arborescence de navigation** exposée à Android Auto :
- `À suivre` — la file Poulpify, « ajouté par X » en sous-titre
- `Passagers` — informatif
- `Mes playlists` → titres ; première ligne « ▶ Jouer cette playlist maintenant »,
  les autres = « ajouter à la file »
- `Titres likés` / `Top titres` / `Écoutés récemment`

**Recherche vocale** : `onSearch` / `onPlayFromSearch` → `/api/search`.

**Custom commands du `MediaSession`** (boutons dans l'écran de lecture AA) :
`🔒 Verrouiller la file`, `⏭ Skip hôte`, `🐙 Ajout surprise`.

Ce service **est** le service de premier plan : il porte la notification et le type
`mediaPlayback` légitimement, là où l'actuel l'usurpe. `PoulpifyMediaService` disparaît.

**Manifeste** — `automotive_app_desc.xml` doit déclarer les deux usages :
```xml
<automotiveApp>
    <uses name="media" />
    <uses name="template" />
</automotiveApp>
```
plus l'`intent-filter` `androidx.media3.session.MediaLibraryService` +
`android.media.browse.MediaBrowserService`.

---

## 7. Phase 4 — Car App Library : tableau de bord hôte (1–2 j)

`PoulpifyCarAppService`, catégorie `IOT` (acceptable en sideload), `HostValidator`
permissif **uniquement en debug**. Tous les écrans sont alimentés par
`SessionCoordinator.state` avec `Screen.invalidate()` sur changement.

- **`DashboardScreen`** — état de session, titre en cours + « ajouté par », votes X/Y,
  nombre de passagers, actions Verrou / Skip / QR
- **`QueueScreen`** — file, en respectant
  `ConstraintManager.getContentLimit(CONTENT_LIMIT_TYPE_LIST)` au lieu du `take(5)` en dur
- **`PassengersScreen`** — liste avec emoji
- **`LibraryScreen`** (GridTemplate) → **`TrackListScreen`** → tap = ajout à la file,
  `CarToast` de confirmation
- **`SessionScreen`** — QR généré à la volée (ZXing) au lieu du PNG figé, redimensionné
  pour la limite binder de 1 Mo (la logique actuelle est bonne, à conserver)
- **`ErrorScreen`** — `MessageTemplate` avec action « Réessayer » pour chaque état dégradé

---

## 8. Phase 5 — App téléphone en Compose (1–2 j)

Remplace les 454 lignes de XML et les `findViewById`.

- **Configuration** (premier lancement) : URL du serveur, mot de passe hôte, nom + emoji
  du conducteur, connexion Spotify (App Remote + OAuth serveur)
- **Tableau de bord** : version riche de l'écran voiture — pochette, progression, file
  complète, passagers. C'est la seule surface où le karaoké est acceptable.
- **Diagnostic** — l'écran qui manque aujourd'hui : état de chaque brique (serveur,
  token hôte, OAuth Spotify, App Remote, device Spotify actif, dernier heartbeat) avec
  un bouton « Tester ». Sans ça, impossible de savoir *pourquoi* ça ne marche pas.
- **Réglages** : démarrage auto à la connexion Android Auto, déconnexion auto,
  comportement du tap (ajouter vs jouer).

**Démarrage automatique en tant qu'hôte** — la demande centrale : observer
`CarConnection.getInstance(context).type` (androidx.car.app) ; dès que le type passe à
`CONNECTION_TYPE_PROJECTION`, démarrer le service et lancer `autoLogin()`. Le conducteur
branche son téléphone, la session hôte s'ouvre, rien à toucher.

---

## 9. Phase 6 — Robustesse et vérification (1 j)

- Tests unitaires du `SessionCoordinator` (Turbine + MockWebServer) : login, perte
  réseau, 403 → re-login, App Remote absent, serveur injoignable
- `HttpLoggingInterceptor` uniquement `if (BuildConfig.DEBUG)` *(corrige le n°7)*
- Suppression de `usesCleartextTraffic`, `networkSecurityConfig` HTTPS strict
- R8 activé en release + règles proguard pour les DTO
- **`git init`** sur PoulpifyAuto (aucun historique aujourd'hui) + CI Gradle
- Checklist de validation en voiture réelle (voir §11)

---

## 10. Limites honnêtes

1. **Impossible de supprimer ou réordonner la file.** L'API Spotify n'expose aucun
   endpoint pour ça. Skip uniquement — pas de contournement.
2. **Deux apps média coexisteront** dans le sélecteur d'Android Auto (Spotify + Poulpify).
   C'est voulu : Spotify produit le son, Poulpify pilote et affiche le contexte social.
3. **Sideload** : il faut activer « Sources inconnues » dans les paramètres développeur
   d'Android Auto, sinon l'app n'apparaît pas sur l'écran voiture.
4. **Play Store** n'apporterait qu'une chose : l'indexation par l'Assistant pour
   « joue X *sur Poulpify* ». La recherche vocale depuis l'UI média d'AA fonctionne
   en sideload.
5. **Spotify Premium obligatoire** (queue et skip via Web API comme via App Remote).
6. **Le karaoké ne passe pas en voiture** — distraction du conducteur, et Android Auto
   ne rendra pas de texte défilant. Il reste sur le téléphone et chez les passagers.

---

## 11. Checklist de validation finale

- [ ] Téléphone branché en Android Auto → session hôte ouverte sans intervention
- [ ] Un passager scanne le QR, rejoint, ajoute un titre → il apparaît dans la file
      voiture avec son nom
- [ ] Play / pause / next depuis l'écran voiture → effet immédiat (< 300 ms)
- [ ] Recherche vocale depuis l'UI média d'AA → titre ajouté à la file
- [ ] Verrouillage de la file depuis la voiture → les invités sont bloqués sur le site
- [ ] Mode avion 30 s → reconnexion automatique, aucune session perdue
- [ ] App tuée puis relancée → **le site web reste connecté à Spotify**
- [ ] App Spotify fermée de force → message clair sur l'écran voiture, pas de plantage
- [ ] Aucun passager éjecté lors d'un redémarrage de l'app auto

---

## 12. Effort estimé

| Phase | Contenu | Effort |
|---|---|---|
| 0 | Correctifs + endpoints serveur | ½ j |
| 1 | Socle Android, modules, SessionCoordinator | 1–2 j |
| 2 | Spotify App Remote + config dashboard | 1 j |
| 3 | MediaLibraryService (surface voiture principale) | 2 j |
| 4 | Car App Library (dashboard hôte) | 1–2 j |
| 5 | App téléphone Compose + démarrage auto | 1–2 j |
| 6 | Robustesse, tests, packaging | 1 j |
| | **Total** | **8–11 j** |

La phase 0 est indépendante et livre déjà un gain visible : elle arrête de casser la
session Spotify du site à chaque fermeture de l'app auto.
