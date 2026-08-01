# Brouillon d'issue "Request For Packaging"

Point d'entrée recommandé par F-Droid pour une première soumission :
https://gitlab.com/fdroid/rfp/-/issues/new (nécessite un compte gitlab.com,
distinct de git.notarobot.xyz).

Ce fichier n'est soumis nulle part automatiquement : c'est un brouillon prêt à
copier-coller quand tu décides de soumettre. Vérifie d'abord le rappel dans
`fdroid/metadata-draft.yml` (couper un nouveau tag après le fix de signature).

---

**Titre de l'issue :**

Excerpta

**Corps de l'issue :**

- **Application name**: Excerpta
- **Source code**: https://github.com/notarobot63/excerpta-android
- **Issue tracker**: https://github.com/notarobot63/excerpta-android/issues
- **License**: AGPL-3.0-only ([LICENSE](https://github.com/notarobot63/excerpta-android/blob/main/LICENSE))
- **Description**:

  Android companion app for [Excerpta](https://github.com/notarobot63/excerpta),
  a self-hosted, open-source link manager. Paginated link list, navigation
  drawer (groups + tags), quick add via Android share sheet, offline cache,
  QR code pairing, encrypted API key storage (EncryptedSharedPreferences /
  AES-256), adaptive icon, several color themes.

  The app is a client only: it talks to a self-hosted Excerpta server (also
  AGPL-3.0, no proprietary backend, no third-party service involved). Pairing
  is done once by scanning a QR code from the server's own web settings page.
  No ads, no analytics, no trackers.

- **Permissions**: `INTERNET` (talk to the configured server), `CAMERA`
  (QR code pairing scan only, no photo/video captured or stored).
- **Dependencies**: AndroidX (AppCompat, Lifecycle, RecyclerView, security-crypto,
  SwipeRefreshLayout), Material Components, Kotlin Coroutines, ZXing
  (`zxing-android-embedded`), Coil. All FOSS (Apache-2.0 / MIT), no Google Play
  Services, no Firebase, no ads/analytics SDK.
- **Build**: Gradle, no NDK, no submodules. Builds cleanly with
  `./gradlew assembleRelease` from a bare clone (no secret required — see fix
  mentioned above). `versionCode` = commit count (`git rev-list --count`),
  `versionName` = git tag without its `v` prefix.

---

## Notes pour toi (Thomas), pas pour l'issue

- Compte gitlab.com nécessaire (séparé de git.notarobot.xyz).
- F-Droid préfère souvent qu'un mainteneur *ouvre l'RFP* plutôt que de soumettre
  directement une MR sur fdroiddata — les volontaires F-Droid rédigent alors
  eux-mêmes la recette à partir de ces infos. Tu peux aussi joindre
  `fdroid/metadata-draft.yml` en commentaire si tu préfères leur mâcher le travail.
- Un reviewer F-Droid clonera le dépôt et lancera le build tel quel : le fix de
  signature (2026-08-01) est donc un prérequis réel, pas cosmétique.
- Screenshots + descriptions courte/longue sont déjà en place dans
  `fastlane/metadata/android/{en-US,fr-FR}/` : F-Droid les récupère
  automatiquement depuis le dépôt, aucune action supplémentaire nécessaire
  pour la fiche du store.
