package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.ExpenseCategory
import com.premraj.notiflow.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseParserTest {

    @Test
    fun testParseUpiDebit() {
        val title = "HDFC Bank Alert"
        val body = "Rs 450.00 debited from A/C XX1234 on 26-09-24 to Swiggy via UPI. Avail Bal: Rs 12,450.00"
        val tx = ExpenseParser.parse(title, body, "com.hdfc.bank", "HDFC Bank")

        assertNotNull(tx)
        assertEquals(450.0, tx!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, tx.transactionType)
        assertEquals(ExpenseCategory.FOOD, tx.expenseCategory)
    }

    @Test
    fun testParseSalaryCredit() {
        val title = "Salary Credited"
        val body = "INR 45,000.00 credited to your account XX5678 towards monthly salary. Avail bal INR 60,000.00"
        val tx = ExpenseParser.parse(title, body, "com.sbi.upi", "SBI")

        assertNotNull(tx)
        assertEquals(45000.0, tx!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, tx.transactionType)
        assertEquals(ExpenseCategory.GENERAL, tx.expenseCategory)
    }

    @Test
    fun testNonFinancialIgnored() {
        val title = "Alice"
        val body = "Hey, let's meet at 5pm!"
        val tx = ExpenseParser.parse(title, body, "com.whatsapp", "WhatsApp")
        assertNull(tx)
    }
}
