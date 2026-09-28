package com.premraj.notiflow.util

import com.premraj.notiflow.data.ExpenseCategory
import com.premraj.notiflow.data.ExpenseTransaction
import com.premraj.notiflow.data.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExporterTest {

    @Test
    fun testExportExpensesToCsv() {
        val transactions = listOf(
            ExpenseTransaction(
                id = 1L,
                notificationId = 101L,
                amount = 450.50,
                transactionType = TransactionType.DEBIT,
                merchantOrParty = "Swiggy",
                accountRef = "XX1234",
                balanceAfter = 12000.0,
                expenseCategory = ExpenseCategory.FOOD,
                timestamp = 1727300000000L
            ),
            ExpenseTransaction(
                id = 2L,
                notificationId = 102L,
                amount = 25000.0,
                transactionType = TransactionType.CREDIT,
                merchantOrParty = "Salary",
                accountRef = "XX5678",
                balanceAfter = 37000.0,
                expenseCategory = ExpenseCategory.TRANSFER,
                timestamp = 1727310000000L
            )
        )

        val csv = BackupExporter.exportExpensesToCsv(transactions)
        assertTrue(csv.contains("ID,Timestamp,Type,Amount,MerchantOrParty,Category,AccountRef,BalanceAfter"))
        assertTrue(csv.contains("Swiggy"))
        assertTrue(csv.contains("450.5"))
        assertTrue(csv.contains("Salary"))
    }

    @Test
    fun testExportExpensesNeutralizesSpreadsheetFormulas() {
        val transactions = listOf(
            ExpenseTransaction(
                id = 3L,
                notificationId = 103L,
                amount = 1.0,
                transactionType = TransactionType.DEBIT,
                merchantOrParty = "=HYPERLINK(\"https://example.invalid\",\"click\")",
                accountRef = "+1234",
                balanceAfter = null,
                expenseCategory = ExpenseCategory.GENERAL,
                timestamp = 1727320000000L
            )
        )

        val csv = BackupExporter.exportExpensesToCsv(transactions)

        assertTrue(csv.contains("\"'=HYPERLINK("))
        assertTrue(csv.contains("\"'+1234\""))
    }

    @Test
    fun testCreateAndDecryptEncryptedBackup() {
        val item = com.premraj.notiflow.data.NotificationItem(
            id = 42L,
            notificationKey = "test_key",
            packageName = "com.test.app",
            appName = "Test App",
            title = "Secret Alert",
            body = "Verification code is 9876",
            sender = "AuthService",
            postedAt = 1727300000000L,
            updatedAt = 1727300000000L,
            removedAt = null,
            isActiveOnSystem = false,
            category = com.premraj.notiflow.data.NotificationCategory.OTP,
            priority = com.premraj.notiflow.data.NotificationPriority.HIGH,
            confidence = 0.99f,
            categoryOverridden = false,
            priorityOverridden = false,
            state = com.premraj.notiflow.data.NotificationState.ACTIVE,
            remindAt = null,
            pinned = true,
            isVip = false,
            read = true,
            amountHint = null
        )
        val vipRules = setOf("com.important.app|", "com.chat.app|Mom")
        val password = "StrongUserPassphrase#2026"

        val encryptedPayload = BackupExporter.createEncryptedBackup(
            notifications = listOf(item),
            vipRules = vipRules,
            password = password
        )

        assertTrue(encryptedPayload.isNotBlank())

        val result = BackupExporter.decryptBackup(encryptedPayload, password)
        assertTrue(result.isSuccess)
        val json = result.getOrThrow()
        org.junit.Assert.assertEquals(1, json.getInt("version"))

        val notifs = json.getJSONArray("notifications")
        org.junit.Assert.assertEquals(1, notifs.length())
        val notifObj = notifs.getJSONObject(0)
        org.junit.Assert.assertEquals(42L, notifObj.getLong("id"))
        org.junit.Assert.assertEquals("Secret Alert", notifObj.getString("title"))
        org.junit.Assert.assertEquals("OTP", notifObj.getString("category"))

        val vips = json.getJSONArray("vip_rules")
        org.junit.Assert.assertEquals(2, vips.length())
    }

    @Test
    fun testDecryptWithWrongPasswordFails() {
        val encryptedPayload = BackupExporter.createEncryptedBackup(
            notifications = emptyList(),
            vipRules = emptySet(),
            password = "CorrectPassword123"
        )

        val result = BackupExporter.decryptBackup(encryptedPayload, "WrongPassword")
        assertTrue(result.isFailure)
    }

    @Test
    fun testDecryptWithCorruptedPayloadFails() {
        val result = BackupExporter.decryptBackup("not-a-valid-base64-payload!!!", "anyPassword")
        assertTrue(result.isFailure)
    }
}

