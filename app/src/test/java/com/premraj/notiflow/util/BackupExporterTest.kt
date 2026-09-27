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
}

