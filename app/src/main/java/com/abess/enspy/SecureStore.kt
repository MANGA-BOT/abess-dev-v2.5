package com.abess.enspy

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("enspy_secure_session", Context.MODE_PRIVATE)
    private val alias = "enspy_local_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            }.doFinal(packed.copyOfRange(12, packed.size)).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    fun clearSession() {
        put("token", null)
        put("student", null)
    }

    fun securePdfFile(documentId: Int): File {
        val dir = File(context.filesDir, "secure_documents").apply { mkdirs() }
        return File(dir, "document_$documentId.bin")
    }

    fun downloadAndEncrypt(urlString: String, documentId: Int, callback: (Boolean, String?) -> Unit) {
        Thread {
            val output = securePdfFile(documentId)
            val result = runCatching {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer ${get("token").orEmpty()}")
                if (connection.responseCode !in 200..299) error("Téléchargement refusé (${connection.responseCode})")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, key())
                }
                FileOutputStream(output).use { fos ->
                    fos.write(cipher.iv)
                    javax.crypto.CipherOutputStream(fos, cipher).use { cos ->
                        connection.inputStream.copyTo(cos)
                    }
                }
                output.absolutePath
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback(result.isSuccess, result.getOrNull())
            }
        }.start()
    }

    fun decryptToCache(encrypted: File, documentId: Int): File {
        val plain = File.createTempFile("enspy_view_$documentId", ".pdf", context.cacheDir)
        FileInputStream(encrypted).use { fis ->
            val iv = ByteArray(12)
            if (fis.read(iv) != 12) error("Fichier invalide")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            }
            javax.crypto.CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(plain).use { fos ->
                    cis.copyTo(fos)
                }
            }
        }
        return plain
    }
}