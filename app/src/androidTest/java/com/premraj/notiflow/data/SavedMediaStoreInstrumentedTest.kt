package com.premraj.notiflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedMediaStoreInstrumentedTest {

    private fun sampleSavedMedia(hash: String, originalUri: String = "content://media/external/images/$hash") =
        SavedMedia(
            id = 0L,
            packageName = "com.whatsapp",
            displayName = "IMG-$hash.jpg",
            kind = SavedMediaKind.IMAGE,
            mimeType = "image/jpeg",
            sizeBytes = 12_345L,
            contentHash = hash,
            savedPath = "/data/saved_media/$hash.jpg",
            originalUri = originalUri,
            capturedAt = 1_760_000_000_000L,
            savedAt = 1_760_000_100_000L,
            notificationId = null
        )

    @Test
    fun insertSavedMediaPersistsRowAcrossRefreshAndDedupesByHash() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("notiflow.db")
        val store = NotificationStore(context)
        store.initialLoadComplete.first { it }

        val rowId = store.insertSavedMedia(sampleSavedMedia("persist-hash-1"))
        // The transaction must commit: -1 means the row was rejected, a rollback would
        // keep refreshSavedMediaFromDb() empty and break the viewer forever.
        assertTrue("insertSavedMedia must return a positive rowId, got $rowId", rowId > 0L)

        val rows = store.savedMediaItems()
        assertEquals(1, rows.size)
        assertEquals("persist-hash-1", rows.first().contentHash)

        val (count, bytes) = store.savedMediaStats()
        assertEquals(1, count)
        assertEquals(12_345L, bytes)

        // Re-inserting the same hash must be rejected without duplicating.
        val duplicateRowId = store.insertSavedMedia(sampleSavedMedia("persist-hash-1"))
        assertEquals(-1L, duplicateRowId)
        assertEquals(1, store.savedMediaItems().size)

        // Distinct hash inserts a second row.
        val secondRowId = store.insertSavedMedia(sampleSavedMedia("persist-hash-2"))
        assertTrue(secondRowId > 0L)
        assertEquals(2, store.savedMediaItems().size)

        // Deleting all rows empties the table and reports which rows were removed.
        val cleared = store.clearedSavedMediaRows()
        assertEquals(2, cleared.size)
        assertEquals(0, store.savedMediaItems().size)
        assertTrue(store.savedMediaStats().first == 0)
    }

    @Test
    fun insertSavedMediaLinksNotificationAndFindsByCaptureWindow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("notiflow.db")
        val store = NotificationStore(context)
        store.initialLoadComplete.first { it }

        val classification = ClassificationResult(
            category = NotificationCategory.MESSAGE,
            priority = NotificationPriority.NORMAL,
            confidence = 0.8f
        )
        val notificationId = store.upsertIncoming(
            incoming = IncomingNotification(
                key = "media-linked-key",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Photo",
                body = "📷 Photo",
                sender = "Sender",
                postedAt = 1_760_000_000_000L
            ),
            classification = classification,
            isVip = false,
            expenseTrackingEnabled = false
        )

        val media = sampleSavedMedia("linked-hash-1").copy(notificationId = notificationId)
        val rowId = store.insertSavedMedia(media)
        assertTrue(rowId > 0L)

        val rows = store.savedMediaItems()
        assertEquals(1, rows.size)
        assertEquals(notificationId, rows.first().notificationId)

        val resolved = store.findNotificationIdForMedia("com.whatsapp", 1_760_000_000_000L)
        assertEquals(notificationId, resolved)

        // Deleting the notification row must not break media lookups (best-effort link).
        store.delete(notificationId)
        assertEquals(1, store.savedMediaItems().size)
    }
}
