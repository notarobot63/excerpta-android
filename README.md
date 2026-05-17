# Excerpta Android

Application Android companion pour [Excerpta](https://GIT_HOST/Thomas/excerpta), le gestionnaire de liens self-hosted.

> collect • annotate • remember

## Fonctionnalités

- **Liste de liens** paginée avec chargement à la demande (swipe-to-refresh)
- **Drawer de navigation** : groupes hiérarchiques puis tags
- **Ajout rapide** via le FAB ou le bouton de partage depuis Chrome/Firefox/etc.
- **Actions contextuelles** (appui long) : ouvrir, copier l'URL, rendre public/privé, supprimer
- **Cache hors-ligne** : les liens restent accessibles sans réseau
- **Configuration par QR code** : scanner le QR depuis l'interface web (Paramètres)
- **Clé API chiffrée** localement (EncryptedSharedPreferences / AES-256)
- **Icône adaptative** Material Design

## Téléchargement

La dernière version est disponible dans les [releases](https://GIT_HOST/Thomas/excerpta-android/releases) sous le nom `excerpta-android.apk`.

Chaque push sur `main` déclenche un build CI qui met à jour automatiquement la release `latest`.

## Configuration

1. Installer l'APK
2. Ouvrir l'application → scanner le QR code affiché dans **Excerpta → Paramètres**
3. L'URL du serveur et la clé API sont configurées automatiquement

## Compilation

```bash
# Cloner le dépôt
git clone https://GIT_HOST/Thomas/excerpta-android.git
cd excerpta-android

# Build debug (sans signature)
./gradlew assembleDebug
```

L'APK produit se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

## Stack

- Kotlin + Coroutines
- Material Design 3
- EncryptedSharedPreferences (security-crypto)
- ZXing (scan QR code)

## Licence

Usage privé / self-hosted.
