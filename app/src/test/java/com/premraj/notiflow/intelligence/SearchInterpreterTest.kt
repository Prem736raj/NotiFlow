package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchInterpreterTest {
    @Test
    fun understandsPaymentAmountWithoutLeavingNoiseText() {
        val intent = SearchInterpreter.interpret("find the payment around 2000 rupees")
        assertEquals(NotificationCategory.PAYMENT, intent.category)
        assertEquals(2000.0, intent.amount ?: 0.0, 0.001)
        assertTrue(intent.freeText.isBlank())
    }

    @Test
    fun keepsSourceNameWhileExtractingDeliveryAndDateWindow() {
        val intent = SearchInterpreter.interpret("Amazon delivery notifications from last week")
        assertEquals(NotificationCategory.DELIVERY, intent.category)
        assertEquals("amazon", intent.freeText)
        assertNotNull(intent.from)
    }
}
