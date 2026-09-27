package com.premraj.notiflow.data

enum class VoiceTriggerCondition(val label: String) {
    HEADPHONES_ONLY("Headphones only"),
    DRIVING_OR_HEADPHONES("Driving or headphones"),
    ALWAYS("Always")
}

enum class VoiceReaderFilter(val label: String) {
    VIP_ONLY("VIP only"),
    MESSAGES_ONLY("Messages only"),
    ALL_EXCEPT_OTP("All except OTP")
}

enum class VoiceReadingDetail(val label: String) {
    SENDER_ONLY("Sender only"),
    FULL_MESSAGE("Full message")
}
