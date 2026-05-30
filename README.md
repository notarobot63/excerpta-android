# Excerpta Android

Application Android companion pour [Excerpta](../excerpta), le gestionnaire de liens self-hosted.

> collect • annotate • remember

[![Get it on Obtainium](https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png)](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://git.notarobot.xyz/thomas/excerpta-android/-/raw/main/obtainium.html)

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

**Obtainium (recommandé)** : clique sur le badge ci-dessus, ou ajoute manuellement cette URL dans Obtainium (mises à jour automatiques) :

```
https://git.notarobot.xyz/thomas/excerpta-android/-/raw/main/obtainium.html
```

**APK direct** (toujours la dernière version) :

```
https://git.notarobot.xyz/thomas/excerpta-android/-/releases/permalink/latest/downloads/excerpta-android.apk
```

Chaque push sur `main` déclenche un build CI qui publie une nouvelle release signée. Le badge/URL Obtainium pointe vers la dernière en date.

## Configuration

1. Installer l'APK
2. Ouvrir l'application → scanner le QR code affiché dans **Excerpta → Paramètres**
3. L'URL du serveur et la clé API sont configurées automatiquement

## Compilation

```bash
git clone <url-du-dépôt>
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

[GNU Affero General Public License v3.0](LICENSE) — fork libre, copyleft fort, attribution obligatoire.
