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

    @Test
    fun testPaymentDueReminderNotParsedAsExpense() {
        val title = "Airtel Bill Reminder"
        val body = "Your postpaid bill payment of Rs 899 is due on 30-Sep-2026. Pay now to avoid late fee."
        val tx = ExpenseParser.parse(title, body, "com.myairtelapp", "Airtel")
        assertNull("Payment due reminder must not be parsed as a completed expense", tx)
    }

    @Test
    fun testPaymentDueWithActualDebitParsedAsExpense() {
        val title = "Auto-Debit Success"
        val body = "Rs 899.00 debited from A/C XX9999 for Airtel bill payment due on 30-Sep. Avail bal: Rs 4,101.00"
        val tx = ExpenseParser.parse(title, body, "com.hdfc.bank", "HDFC Bank")
        assertNotNull(tx)
        assertEquals(899.0, tx!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, tx.transactionType)
        assertEquals("A/c **9999", tx.accountRef)
        assertEquals(4101.0, tx.balanceAfter!!, 0.01)
    }

    @Test
    fun testParseCardDebitWithIndianGrouping() {
        val title = "ICICI Card Alert"
        val body = "Dear Customer, your Credit Card XX4321 was charged ₹1,25,000.50 on 28-Sep at Amazon. Avail limit: ₹2,75,000"
        val tx = ExpenseParser.parse(title, body, "com.icicibank.mobile", "iMobile")
        assertNotNull(tx)
        assertEquals(125000.50, tx!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, tx.transactionType)
        assertEquals("Amazon", tx.merchantOrParty)
        assertEquals("A/c **4321", tx.accountRef)
    }

    @Test
    fun testParseCashWithdrawal() {
        val title = "ATM Cash Withdrawal"
        val body = "INR 5,000.00 withdrawn from ATM using Debit Card XX7890. Avail Bal: INR 15,200.00"
        val tx = ExpenseParser.parse(title, body, "com.sbi.upi", "SBI")
        assertNotNull(tx)
        assertEquals(5000.0, tx!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, tx.transactionType)
        assertEquals(15200.0, tx.balanceAfter!!, 0.01)
    }

    @Test
    fun testParseRefundCredit() {
        val title = "Refund Received"
        val body = "Refund of ₹340.00 from Zomato credited to your UPI linked bank account XX1111."
        val tx = ExpenseParser.parse(title, body, "com.phonepe.app", "PhonePe")
        assertNotNull(tx)
        assertEquals(340.0, tx!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, tx.transactionType)
        assertEquals("Zomato", tx.merchantOrParty)
    }
}
