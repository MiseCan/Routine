package com.routine

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only ciphertext is persisted. The AES key is generated inside Android Keystore. */
class SecretVault(context: Context) {
    private val file=AtomicFile(File(context.filesDir,"deepseek-key.enc"))
    fun exists()=file.baseFile.exists()
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey("routine-deepseek",null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("routine-deepseek",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    fun save(secret: String) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        val envelope=JSONObject().put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).put("data",Base64.encodeToString(cipher.doFinal(secret.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)).toString()
        val stream=file.startWrite()
        try {stream.write(envelope.toByteArray(Charsets.UTF_8));file.finishWrite(stream)} catch(e:Exception){file.failWrite(stream);throw e}
    }
    fun read(): String {
        check(exists()) {"请先在设置中保存 DeepSeek API 密钥。"}
        try {
            val data=JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use {it.readText()})
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(data.getString("iv"),Base64.NO_WRAP)))}
            return String(cipher.doFinal(Base64.decode(data.getString("data"),Base64.NO_WRAP)),Charsets.UTF_8)
        } catch(e:Exception){throw IllegalStateException("无法读取已保存的密钥，请在设置中重新填写。")}
    }
}
