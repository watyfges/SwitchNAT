package dev.switchnat.app.xray

import java.nio.charset.StandardCharsets
import java.util.Base64

internal object XudpBaseKey {
    fun fromDeviceId(deviceId: String?): String {
        if (deviceId.isNullOrBlank()) return ""
        val key = deviceId.toByteArray(StandardCharsets.UTF_8).copyOf(KEY_SIZE_BYTES)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key)
    }

    private const val KEY_SIZE_BYTES = 32
}
