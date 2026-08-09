# PoulpifyAuto 🐙🚗

Application Android qui ouvre automatiquement une session **hôte** Poulpify et
donne au conducteur, sur l'écran d'Android Auto, les mêmes possibilités que
l'interface hôte du site.

Le plan de refonte et le diagnostic de l'ancienne version sont dans [PLAN.md](PLAN.md).

---

## Architecture

```
                       ┌──────────────────────────┐
                       │  Serveur Poulpify (Node) │
                       └────┬─────────────────┬───┘
                    SSE +   │                 │   OAuth Spotify
                    REST    │                 │
        ┌───────────────────┴───┐         ┌───┴────────────┐
        │  SessionCoordinator   │         │  Spotify Cloud │
        │  (source de vérité)   │         └───┬────────────┘
        └───┬──────┬──────┬─────┘             │
            │      │      │   App Remote      │
            │      │      └──────────────► App Spotify ──► audio voiture
   ┌────────┴──┐ ┌─┴────────────────┐ ┌──────────────────┐
   │ Car App   │ │ MediaLibrary     │ │ App téléphone    │
   │ Library   │ │ Service (media3) │ │ (Compose)        │
   └───────────┘ └──────────────────┘ └──────────────────┘
```

Un seul `StateFlow` alimente les trois surfaces. Aucune ne maintient d'état
propre ni ne parle directement au réseau.

| Fonction | Source |
|---|---|
| Play/pause/next/seek, état, position, pochette | **App Remote** (poussé, instantané) |
| Ajout à la file, file à venir, recherche, passagers, votes, verrou | **Serveur** |

L'ajout à la file passe volontairement par le serveur : c'est lui qui tient
l'attribution « ajouté par » que voient les passagers et qui applique le verrou.

### Modules

| Module | Rôle |
|---|---|
| `core:model` | Types de domaine et ports (`ConfigSource`, `SpotifyRemote`). Kotlin pur, testable en JVM. |
| `core:network` | Retrofit + kotlinx.serialization, client SSE avec reconnexion. Kotlin pur. |
| `core:data` | DataStore + chiffrement Keystore du mot de passe hôte. |
| `core:spotify` | Enveloppe Spotify App Remote. Seul module à voir les classes du SDK. |
| `core:session` | `SessionCoordinator` : la source de vérité. |
| `media` | `MediaLibraryService` + `SpotifyProxyPlayer` — surface média d'Android Auto. |
| `car` | Écrans Car App Library — tableau de bord hôte. |
| `app` | Application, UI Compose, câblage du graphe. |

---

## Installation

### 1. Prérequis

- Android Studio récent (JDK 21 fourni)
- Un téléphone avec **Spotify installé** et un compte **Premium**
- Un serveur Poulpify déployé (dépôt voisin `Poulpify`), en version ≥ 2.0.0

### 2. Enregistrer l'application dans le dashboard Spotify

**Sans cette étape, App Remote refuse de se connecter et rien ne fonctionnera.**

Sur [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard),
dans l'app Poulpify → Settings → Android :

| Champ | Valeur |
|---|---|
| Package name | `fr.maxboudier.poulpifyauto` |
| SHA-1 fingerprint | celle de la clé **debug** *et* celle de la clé **release** |

Puis dans Redirect URIs, ajouter : `poulpifyauto://callback`

Pour obtenir l'empreinte de la clé debug :

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

### 3. Renseigner le client ID

Dans `local.properties` (non versionné) :

```properties
spotify.clientId=votre_client_id_spotify
```

### 4. Compiler et installer

```bash
./gradlew :app:installDebug
```

### 5. Activer les sources inconnues dans Android Auto

Sans ce réglage, une application sideloadée **n'apparaît pas** sur l'écran de la
voiture.

1. Ouvrir l'application Android Auto sur le téléphone
2. Appuyer dix fois sur « Version » pour débloquer le mode développeur
3. Menu ⋮ → Paramètres développeur → cocher **Sources inconnues**

### 6. Configurer l'application

Au premier lancement, onglet **Réglages** :
- URL du serveur Poulpify
- Mot de passe hôte (chiffré par le Keystore, jamais stocké en clair)
- Nom et emoji affichés aux passagers

L'onglet **Diagnostic** indique l'état de chaque brique de la chaîne et le geste
correctif quand l'une est en défaut.

---

## Utilisation

Le téléphone se branche à Android Auto → la session hôte s'ouvre seule. Sur
l'écran de la voiture, Poulpify apparaît à deux endroits :

- **dans la liste des applications média** : navigation dans la bibliothèque
  (file, playlists, likés, top, récents), écran de lecture, recherche vocale ;
- **dans la liste des applications** (tableau de bord) : titre en cours et qui
  l'a ajouté, votes, passagers, verrou de file, QR code d'invitation.

Spotify continue de produire le son ; Poulpify pilote et apporte le contexte
social. Les deux applications coexistent donc dans le sélecteur média — c'est
voulu.

---

## Tests

```bash
./gradlew :core:model:test :core:network:test :core:session:testDebugUnitTest
```

Les tests du `SessionCoordinator` couvrent la connexion automatique, le mot de
passe incorrect, l'état dégradé, le repli App Remote → serveur, la
ré-authentification après un 403 et le comptage de références de session.

---

## Limites connues

1. **Aucune suppression ni réordonnancement de la file** : l'API Spotify ne
   l'expose pas. Seul le skip est possible.
2. **Spotify Premium obligatoire.**
3. **Le karaoké reste sur le téléphone** : Android Auto n'affichera pas de texte
   défilant, et cela n'a pas sa place devant un conducteur.
4. **Catégorie `IOT`** pour le tableau de bord : acceptable en sideload, mais un
   dépôt sur le Play Store demanderait une catégorie approuvée par Google.
5. L'état de session vit en mémoire côté serveur ; un redéploiement remet la
   file et les passagers à zéro (le jeton hôte et les jetons Spotify, eux,
   survivent).

---

## Contraintes de build

Deux versions sont épinglées volontairement, avec la raison en commentaire dans
`gradle/libs.versions.toml` :

- `androidx.core:core-ktx` reste en **1.18.0** : la 1.19 exige `compileSdk 37`,
  au-delà de ce qu'AGP 9.1 supporte.
- `coil` reste en **3.3.0** : les versions suivantes sont compilées avec Kotlin
  2.3+, illisible par le compilateur Kotlin 2.2.10 qu'embarque AGP 9.1.

Depuis AGP 9, le plugin Kotlin est intégré : les modules Android ne doivent
**pas** appliquer `org.jetbrains.kotlin.android`, et les modules JVM référencent
`org.jetbrains.kotlin.jvm` sans version.
