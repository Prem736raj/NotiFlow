package com.premraj.notiflow.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.UserPreferences
import com.premraj.notiflow.data.VoiceReaderFilter
import com.premraj.notiflow.data.VoiceReadingDetail
import com.premraj.notiflow.data.VoiceTriggerCondition
import com.premraj.notiflow.intelligence.ActionExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceReaderEngine(
    private val context: Context,
    private val preferences: UserPreferences
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isHeadphonesConnected = MutableStateFlow(false)
    val isHeadphonesConnected: StateFlow<Boolean> = _isHeadphonesConnected.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
        checkAudioDevices()
        registerAudioDeviceCallback()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale.getDefault()
        }
    }

    fun isHeadsetPluggedIn(): Boolean {
        val am = audioManager ?: return false
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
                else -> android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        }
    }

    private fun checkAudioDevices() {
        _isHeadphonesConnected.value = isHeadsetPluggedIn()
    }

    private fun registerAudioDeviceCallback() {
        try {
            val callback = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    checkAudioDevices()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    checkAudioDevices()
                }
            }
            audioDeviceCallback = callback
            audioManager?.registerAudioDeviceCallback(callback, null)
        } catch (_: Exception) {}
    }

    fun shouldAnnounce(item: NotificationItem): Boolean {
        if (!preferences.voiceReaderEnabled) return false
        if (VoiceReaderPolicy.isOtp(item)) return false

        val am = audioManager
        if (am != null && am.mode == AudioManager.MODE_IN_CALL) return false

        val condition = preferences.voiceTriggerCondition
        val headphonesActive = isHeadsetPluggedIn()
        val drivingActive = preferences.drivingModeActive

        val conditionMet = when (condition) {
            VoiceTriggerCondition.HEADPHONES_ONLY -> headphonesActive
            VoiceTriggerCondition.DRIVING_OR_HEADPHONES -> headphonesActive || drivingActive
            VoiceTriggerCondition.ALWAYS -> true
        }
        if (!conditionMet) return false

        val filter = preferences.voiceReaderFilter
        val allowedByFilter = when (filter) {
            VoiceReaderFilter.VIP_ONLY -> preferences.isVip(item.packageName, item.sender)
            VoiceReaderFilter.MESSAGES_ONLY -> item.category == NotificationCategory.MESSAGE
            VoiceReaderFilter.ALL_EXCEPT_OTP -> true
        }
        return allowedByFilter
    }

    fun buildAnnouncement(item: NotificationItem): String =
        VoiceReaderPolicy.buildAnnouncement(item, preferences.voiceReadingDetail)

    fun speak(item: NotificationItem) {
        if (!shouldAnnounce(item)) return
        val speech = buildAnnouncement(item)
        speakText(speech)
    }

    fun testAnnouncement() {
        speakText("NotiFlow hands-free reader is working perfectly.")
    }

    fun speakText(text: String) {
        if (!isInitialized) return
        val ttsEngine = tts ?: return
        ttsEngine.setSpeechRate(preferences.voiceSpeechRate)
        val params = android.os.Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_NOTIFICATION)
        ttsEngine.speak(text, TextToSpeech.QUEUE_ADD, params, "notiflow_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        audioDeviceCallback?.let { callback ->
            try {
                audioManager?.unregisterAudioDeviceCallback(callback)
            } catch (_: Exception) {}
            audioDeviceCallback = null
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

object VoiceReaderPolicy {
    fun isOtp(item: NotificationItem): Boolean =
        item.category == NotificationCategory.OTP ||
            ActionExtractor.extractOtpCode(item.title, item.body) != null

    fun buildAnnouncement(item: NotificationItem, detail: VoiceReadingDetail): String {
        val appName = item.appName.ifBlank { "New notification" }
        val sender = item.sender?.takeIf { it.isNotBlank() }

        return when (detail) {
            VoiceReadingDetail.SENDER_ONLY -> {
                if (sender != null) "$appName from $sender" else appName
            }
            VoiceReadingDetail.FULL_MESSAGE -> {
                val cleanText = item.body?.replace("\n", " ")?.take(120).orEmpty()
                if (sender != null) {
                    "$appName from $sender: $cleanText"
                } else if (cleanText.isNotBlank()) {
                    "$appName: $cleanText"
                } else {
                    appName
                }
            }
        }
    }
}
