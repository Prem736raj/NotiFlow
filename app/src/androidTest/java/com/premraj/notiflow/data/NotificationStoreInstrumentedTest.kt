package com.premraj.notiflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationStoreInstrumentedTest {

    @Test
    fun concurrentSameKeyUpsertsReuseOneStableRowId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("notiflow.db")
        val store = NotificationStore(context)
        store.initialLoadComplete.first { it }

        val classification = ClassificationResult(
            category = NotificationCategory.MESSAGE,
            priority = NotificationPriority.NORMAL,
            confidence = 0.8f
        )
        val ids = coroutineScope {
            (0 until 100).map { update ->
                async(Dispatchers.Default) {
                    store.upsertIncoming(
                        incoming = IncomingNotification(
                            key = "same-notification-key",
                            packageName = "com.example.chat",
                            appName = "Example Chat",
                            title = "Update $update",
                            body = "Message $update",
                            sender = "Sender",
                            postedAt = 1_000_000L + update
                        ),
                        classification = classification,
                        isVip = false,
                        expenseTrackingEnabled = false
                    )
                }
            }.awaitAll()
        }

        assertEquals(1, ids.toSet().size)
        assertEquals(1, store.items.value.count { it.notificationKey == "same-notification-key" })
        assertEquals(ids.first(), store.get(ids.first())?.id)
    }
}
