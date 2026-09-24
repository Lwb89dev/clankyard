package dev.clankyard.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private const val KEY_ALIAS = "clankyard.credentials.v1"
private const val CREDENTIALS_DIR = "credentials"

/** Production Keystore-backed store. JVM tests must not call this. */
fun androidKeystoreCredentialStore(context: Context): SecureCredentialStore {
    return AesGcmCredentialStore(
        directory = File(context.filesDir, CREDENTIALS_DIR),
        key = keystoreAesKey(context),
    )
}

internal fun keystoreAesKey(context: Context): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    if (existing != null) return existing
    val strongBox = context.packageManager.hasSystemFeature(
        PackageManager.FEATURE_STRONGBOX_KEYSTORE,
    )
    if (!strongBox) return generateKey(strongBox = false)
    return try {
        generateKey(strongBox = true)
    } catch (_: Exception) {
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
        generateKey(strongBox = false)
    }
}

private fun generateKey(strongBox: Boolean): SecretKey {
    val spec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
        .setKeySize(256)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .setUserAuthenticationRequired(false)
        .setIsStrongBoxBacked(strongBox)
        .build()
    val generator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore",
    )
    generator.init(spec)
    return generator.generateKey()
}
