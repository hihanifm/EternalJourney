package com.hanifm.eternaljourney.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "eternal_journey_prefs"
        private const val KEY_SELECTED_DEVICES = "selected_devices"
        private const val KEY_DEFAULT_AUDIO_FILE = "default_audio_file"
        private const val KEY_AUTO_PLAY_ENABLED = "auto_play_enabled"
        private const val DELIMITER = ","
    }

    fun getSelectedDeviceAddresses(): Set<String> {
        val devicesString = prefs.getString(KEY_SELECTED_DEVICES, "") ?: ""
        return if (devicesString.isEmpty()) {
            emptySet()
        } else {
            devicesString.split(DELIMITER).toSet()
        }
    }

    fun saveSelectedDeviceAddresses(addresses: Set<String>) {
        prefs.edit()
            .putString(KEY_SELECTED_DEVICES, addresses.joinToString(DELIMITER))
            .apply()
    }

    fun getDefaultAudioFile(): String? {
        return prefs.getString(KEY_DEFAULT_AUDIO_FILE, null)
    }

    fun setDefaultAudioFile(fileName: String?) {
        prefs.edit()
            .putString(KEY_DEFAULT_AUDIO_FILE, fileName)
            .apply()
    }

    fun isAutoPlayEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_PLAY_ENABLED, true)
    }

    fun setAutoPlayEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_PLAY_ENABLED, enabled)
            .apply()
    }
}

