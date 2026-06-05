package com.foxplayer.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.PreferenceFragmentCompat
import com.foxplayer.R
import com.foxplayer.util.ThemeHelper

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        findPreference<androidx.preference.Preference>("theme")?.setOnPreferenceChangeListener { _, newVal ->
            ThemeHelper.setTheme(requireContext(), (newVal as String).toInt())
            true
        }
    }
}
