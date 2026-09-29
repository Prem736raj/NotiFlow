package com.premraj.notiflow.util

import android.content.Context
import com.premraj.notiflow.data.ExpenseTransaction
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.UserPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupExporter {

    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    fun exportExpensesToCsv(transactions: List<ExpenseTransaction>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("ID,Timestamp,Type,Amount,MerchantOrParty,Category,AccountRef,BalanceAfter\n")
        transactions.forEach { t ->
            val dateStr = dateFormat.format(Date(t.timestamp))
            val safeMerchant = sanitizeCsvText(t.merchantOrParty ?: "Unknown")
            val safeAccount = sanitizeCsvText(t.accountRef ?: "")
            val balanceStr = t.balanceAfter?.toString() ?: ""
            sb.append("${t.id},\"$dateStr\",${t.transactionType.name},${t.amount},\"$safeMerchant\",${t.expenseCategory.name},\"$safeAccount\",\"$balanceStr\"\n")
        }
        return sb.toString()
    }

    private fun sanitizeCsvText(value: String): String {
        val trimmedLeading = value.dropWhile { it.isWhitespace() }
        val formulaSafe = if (
            trimmedLeading.startsWith("=") ||
            trimmedLeading.startsWith("+") ||
            trimmedLeading.startsWith("-") ||
            trimmedLeading.startsWith("@")
        ) {
            "'$value"
        } else {
            value
        }
        return formulaSafe.replace("\"", "\"\"")
    }

    fun createEncryptedBackup(
        notifications: List<NotificationItem>,
        preferences: UserPreferences,
        password: String
    ): String = createEncryptedBackup(notifications, preferences.vipRules(), password)

    fun createEncryptedBackup(
        notifications: List<NotificationItem>,
        vipRules: Set<String>,
        password: String
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("created_at", System.currentTimeMillis())

        val notifArray = JSONArray()
        notifications.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("package", item.packageName)
            obj.put("app_name", item.appName)
            obj.put("title", item.title.orEmpty())
            obj.put("body", item.body.orEmpty())
            obj.put("sender", item.sender.orEmpty())
            obj.put("posted_at", item.postedAt)
            obj.put("category", item.category.name)
            obj.put("priority", item.priority.name)
            obj.put("pinned", item.pinned)
            notifArray.put(obj)
        }
        root.put("notifications", notifArray)

        val vipArray = JSONArray()
        vipRules.forEach { vipArray.put(it) }
        root.put("vip_rules", vipArray)

        val jsonString = root.toString()
        return encrypt(jsonString, password)
    }

    fun decryptBackup(encryptedPayload: String, password: String): Result<JSONObject> = runCatching {
        val jsonString = decrypt(encryptedPayload, password)
        JSONObject(jsonString)
    }

    fun writeEncryptedBackupToFile(
        context: Context,
        encryptedPayload: String,
        fileName: String = "notiflow-backup-${System.currentTimeMillis()}.enc"
    ): File {
        val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val backupFile = File(backupsDir, fileName)
        backupFile.writeText(encryptedPayload, Charsets.UTF_8)
        return backupFile
    }

    fun createEncryptedBackupFile(
        context: Context,
        notifications: List<NotificationItem>,
        preferences: UserPreferences,
        password: String,
        fileName: String = "notiflow-backup-${System.currentTimeMillis()}.enc"
    ): File {
        val encrypted = createEncryptedBackup(notifications, preferences, password)
        return writeEncryptedBackupToFile(context, encrypted, fileName)
    }

    private fun encrypt(plainText: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

        return Base64Compat.encodeToString(combined)
    }

    private fun decrypt(encryptedBase64: String, password: String): String {
        val combined = Base64Compat.decode(encryptedBase64)
        if (combined.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Invalid payload length")
        }

        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        val cipherText = ByteArray(combined.size - SALT_LENGTH - IV_LENGTH)

        System.arraycopy(combined, 0, salt, 0, SALT_LENGTH)
        System.arraycopy(combined, SALT_LENGTH, iv, 0, IV_LENGTH)
        System.arraycopy(combined, SALT_LENGTH + IV_LENGTH, cipherText, 0, cipherText.size)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decrypted = cipher.doFinal(cipherText)

        return String(decrypted, Charsets.UTF_8)
    }
}

