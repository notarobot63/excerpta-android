package xyz.notarobot.excerpta

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Prefs {
    private const val NAME = "excerpta_prefs_v2"
    private const val NAME_LEGACY_ENCRYPTED = "excerpta_prefs"
    private const val NAME_LEGACY_PLAIN = "excerpta_prefs_plain"
    private const val KEYSTORE_ALIAS = "excerpta_master_key"
    private const val KEY_SERVER = "server_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_THEME = "theme"
    private const val KEY_COLLAPSED_FOLDERS = "collapsed_folders"

    @Volatile private var _prefs: SharedPreferences? = null
    @Volatile private var _encrypted: Boolean = true

    fun isEncrypted(ctx: Context): Boolean { getPrefs(ctx); return _encrypted }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return kg.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (ivPart, cipherPart) = stored.split(":", limit = 2).let { it[0] to it[1] }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(ivPart, Base64.NO_WRAP)),
        )
        return String(cipher.doFinal(Base64.decode(cipherPart, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun putEncrypted(editor: SharedPreferences.Editor, key: String, value: String) {
        editor.putString(key, encrypt(value))
    }

    private fun getEncrypted(prefs: SharedPreferences, key: String, default: String): String {
        val stored = prefs.getString(key, null) ?: return default
        return try {
            decrypt(stored)
        } catch (e: Exception) {
            android.util.Log.w("Prefs", "Dechiffrement echoue pour $key, valeur ignoree", e)
            default
        }
    }

    private fun getPrefs(ctx: Context): SharedPreferences {
        _prefs?.let { return it }
        return synchronized(this) {
            _prefs?.let { return it }
            try {
                getOrCreateKey()
                val store = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                _encrypted = true
                migrateLegacyIfNeeded(ctx, store)
                _prefs = store
                store
            } catch (e: Exception) {
                android.util.Log.w("Prefs", "Keystore indisponible, fallback non chiffre", e)
                _encrypted = false
                ctx.applicationContext.getSharedPreferences(NAME_LEGACY_PLAIN, Context.MODE_PRIVATE)
                    .also { _prefs = it }
            }
        }
    }

    /**
     * androidx.security:security-crypto est deprecie depuis la 1.1.0-beta01 (Google recommande
     * le Keystore direct). Cette migration one-shot relit l'ancien stockage EncryptedSharedPreferences
     * (API toujours fonctionnelle, seulement deprecated) pour reporter les valeurs vers le nouveau
     * stockage chiffre au Keystore, sans jamais deconnecter un utilisateur existant.
     */
    private fun migrateLegacyIfNeeded(ctx: Context, newStore: SharedPreferences) {
        if (newStore.contains(KEY_SERVER) || newStore.contains(KEY_API_KEY)) return
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val legacy = EncryptedSharedPreferences.create(
                NAME_LEGACY_ENCRYPTED, masterKeyAlias, ctx.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val server = legacy.getString(KEY_SERVER, null)
            val apiKey = legacy.getString(KEY_API_KEY, null)
            if (server == null && apiKey == null) return
            newStore.edit().apply {
                server?.let { putEncrypted(this, KEY_SERVER, it) }
                apiKey?.let { putEncrypted(this, KEY_API_KEY, it) }
                legacy.getString(KEY_THEME, null)?.let { putString(KEY_THEME, it) }
                legacy.getStringSet(KEY_COLLAPSED_FOLDERS, null)?.let { putStringSet(KEY_COLLAPSED_FOLDERS, it) }
            }.apply()
            android.util.Log.i("Prefs", "Migration depuis l'ancien stockage EncryptedSharedPreferences reussie")
        } catch (e: Exception) {
            android.util.Log.w("Prefs", "Pas de donnees legacy a migrer (ou echec migration)", e)
        }
    }

    fun serverUrl(ctx: Context): String {
        val prefs = getPrefs(ctx)
        return if (_encrypted) getEncrypted(prefs, KEY_SERVER, "") else prefs.getString(KEY_SERVER, "") ?: ""
    }

    fun apiKey(ctx: Context): String {
        val prefs = getPrefs(ctx)
        return if (_encrypted) getEncrypted(prefs, KEY_API_KEY, "") else prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun save(ctx: Context, serverUrl: String, apiKey: String) {
        val prefs = getPrefs(ctx)
        prefs.edit().apply {
            if (_encrypted) {
                putEncrypted(this, KEY_SERVER, serverUrl.trimEnd('/'))
                putEncrypted(this, KEY_API_KEY, apiKey.trim())
            } else {
                putString(KEY_SERVER, serverUrl.trimEnd('/'))
                putString(KEY_API_KEY, apiKey.trim())
            }
        }.apply()
    }

    fun isConfigured(ctx: Context) = serverUrl(ctx).isNotBlank() && apiKey(ctx).isNotBlank()

    fun theme(ctx: Context): String =
        getPrefs(ctx).getString(KEY_THEME, "light") ?: "light"

    fun saveTheme(ctx: Context, theme: String) {
        getPrefs(ctx).edit()
            .putString(KEY_THEME, theme)
            .apply()
    }

    /** IDs des dossiers repliés dans le drawer (arborescence). */
    fun collapsedFolders(ctx: Context): MutableSet<Int> =
        (getPrefs(ctx).getStringSet(KEY_COLLAPSED_FOLDERS, emptySet()) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }
            .toMutableSet()

    fun saveCollapsedFolders(ctx: Context, ids: Set<Int>) {
        getPrefs(ctx).edit()
            .putStringSet(KEY_COLLAPSED_FOLDERS, ids.map { it.toString() }.toSet())
            .apply()
    }
}
