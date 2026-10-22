package com.premraj.notiflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSaverFilterTest {

    @Test
    fun `relevant WhatsApp paths pass the filter`() {
        assertTrue(MediaSaver.isRelevantPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/IMG-20261022-WA0001.jpg"))
        assertTrue(MediaSaver.isRelevantPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp Video/VID-20261022-WA0002.mp4"))
        assertTrue(MediaSaver.isRelevantPath("\\storage\\emulated\\0\\Android\\media\\com.whatsapp\\WhatsApp\\Media\\WhatsApp Images\\photo.jpg"))
        assertTrue(MediaSaver.isRelevantPath("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/IMG-001.jpg"))
    }

    @Test
    fun `paths outside WhatsApp are rejected`() {
        assertFalse(MediaSaver.isRelevantPath("/storage/emulated/0/DCIM/Camera/IMG.jpg"))
        assertFalse(MediaSaver.isRelevantPath("/storage/emulated/0/com.telegram/media/photo.jpg"))
        assertFalse(MediaSaver.isRelevantPath(null))
        assertFalse(MediaSaver.isRelevantPath(""))
        assertFalse(MediaSaver.isRelevantPath("   "))
    }

    @Test
    fun `hidden folders stickers and temp files are rejected`() {
        assertFalse(MediaSaver.isRelevantPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/private.jpg"))
        assertFalse(MediaSaver.isRelevantPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers/sticker.webp"))
        assertFalse(MediaSaver.isRelevantPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/whatsapp stickers/s.webp"))
        assertFalse(MediaSaver.isRelevantPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/.video.tmp"))
        assertFalse(MediaSaver.isRelevantPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/download.part"))
        assertFalse(MediaSaver.isRelevantPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.nomedia"))
    }

    @Test
    fun `extension mapping covers common mime types`() {
        assertEquals("jpg", MediaSaver.extensionFor("image/jpeg"))
        assertEquals("png", MediaSaver.extensionFor("image/png"))
        assertEquals("webp", MediaSaver.extensionFor("image/webp"))
        assertEquals("gif", MediaSaver.extensionFor("image/gif"))
        assertEquals("mp4", MediaSaver.extensionFor("video/mp4"))
        assertEquals("3gp", MediaSaver.extensionFor("video/3gpp"))
        assertEquals("webm", MediaSaver.extensionFor("video/webm"))
        assertEquals("m4a", MediaSaver.extensionFor("audio/mp4"))
        assertEquals("bin", MediaSaver.extensionFor(null))
        assertEquals("bin", MediaSaver.extensionFor("application/octet-stream"))
        assertEquals("mp4", MediaSaver.extensionFor("VIDEO/MP4"))
    }

    @Test
    fun `base name is sanitized for the filesystem`() {
        assertEquals("IMG-20261022", MediaSaver.sanitizedBaseName("IMG-20261022.jpg"))
        assertEquals("invoice_scan", MediaSaver.sanitizedBaseName("invoice scan.png"))
        assertEquals("a__b__c", MediaSaver.sanitizedBaseName("a//b::c.mov"))
        assertEquals("media", MediaSaver.sanitizedBaseName("___"))
        assertEquals("media", MediaSaver.sanitizedBaseName(""))
        // Very long names are truncated.
        val longName = "x".repeat(120)
        assertEquals(60, MediaSaver.sanitizedBaseName(longName).length)
    }

    @Test
    fun `host package distinguishes WhatsApp and Business`() {
        assertEquals(
            "com.whatsapp.w4b",
            MediaSaver.hostPackageOf("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/x.jpg")
        )
        assertEquals(
            "com.whatsapp",
            MediaSaver.hostPackageOf("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/x.jpg")
        )
    }

    @Test
    fun `scan result summary is user readable`() {
        assertEquals("Media saving is off", MediaScanResult(disabled = true).summary)
        assertEquals(
            "Media saving needs photo and video permission",
            MediaScanResult(missingPermission = true).summary
        )
        assertEquals(
            "No new WhatsApp media found",
            MediaScanResult(scanned = 0).summary
        )
        assertEquals(
            "Saved 3 new file(s) (2 already in NotiFlow)",
            MediaScanResult(scanned = 5, saved = 3, duplicates = 2).summary
        )
        assertEquals(
            "All 4 recent items were already saved",
            MediaScanResult(scanned = 4, duplicates = 4).summary
        )
    }
}
