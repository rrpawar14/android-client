package com.mifos.mifosxdroid

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceFragment
import android.preference.SwitchPreference
import android.widget.Toast
import com.mifos.mifosxdroid.dialogfragments.syncsurveysdialog.SyncSurveysDialogFragment
import com.mifos.utils.FragmentConstants
import com.mifos.utils.LanguageHelper
import com.mifos.utils.ThemeHelper

/**
 * Created by mayankjindal on 22/07/17.
 */
class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    var mEnableSyncSurvey: SwitchPreference? = null
    private lateinit var languages: Array<String>
    private var languageCallback: LanguageCallback? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
        languages = activity.resources.getStringArray(R.array.language_option)
        mEnableSyncSurvey = findPreference(resources.getString(R.string.sync_survey)) as SwitchPreference
        mEnableSyncSurvey!!.onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
            if (newValue as Boolean) {
                val syncSurveysDialogFragment = SyncSurveysDialogFragment.newInstance()
                val fragmentTransaction = fragmentManager.beginTransaction()
                fragmentTransaction.addToBackStack(FragmentConstants.FRAG_SURVEYS_SYNC)
                syncSurveysDialogFragment.isCancelable = false
                syncSurveysDialogFragment.show(fragmentTransaction,
                        resources.getString(R.string.sync_clients))
            }
            true
        }

        val langPref = findPreference("language_type") as ListPreference
        langPref.onPreferenceChangeListener = OnPreferenceChangeListener {preference, newValue ->
            LanguageHelper.setLocale(this.activity, newValue.toString())
            startActivity(Intent(activity, activity.javaClass))
            preferenceScreen = null
            addPreferencesFromResource(R.xml.preferences)
            preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            true
        }

        val themePreference = findPreference(resources.getString(R.string.mode_key)) as ListPreference
        themePreference.onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
            val themeOption = newValue as String
            ThemeHelper.applyTheme(themeOption)
            startActivity(Intent(activity, activity.javaClass))
            Toast.makeText(activity, "Switched to ${themeOption.toString()} Mode", Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    fun setLanguageCallback(languageCallback: LanguageCallback?) {
        this.languageCallback = languageCallback
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, s: String) {
        val preference = findPreference(s) as ListPreference
        LanguageHelper.setLocale(this.activity, preference.value)
    }

    interface LanguageCallback {
        fun updateNavDrawer()
    }

    companion object {
        fun newInstance(): SettingsFragment {
            val fragment = SettingsFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }

        fun newInstance(languageCallback: LanguageCallback?): SettingsFragment {
            val fragment = SettingsFragment()
            fragment.setLanguageCallback(languageCallback)
            return fragment
        }
    }
}