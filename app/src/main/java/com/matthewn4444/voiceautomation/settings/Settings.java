package com.matthewn4444.voiceautomation.settings;

import android.app.ActionBar;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.matthewn4444.voiceautomation.R;
import com.matthewn4444.voiceautomation.settings.preferences.SummaryEditTextPreference;

import java.util.List;

public class Settings extends PreferenceActivity {

    private static final String[] FragmentNames = {
            SpeechPrefFragment.class.getName(),
            LightAutomationPrefFragment.class.getName(),
            MusicPrefFragment.class.getName(),
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(R.string.activity_settings_title);
        }
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.settings_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        for (String name: FragmentNames) {
            if (fragmentName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return true;
    }

    public static class SpeechPrefFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ActionBar bar = getActivity().getActionBar();
            if (bar != null) {
                bar.setTitle(R.string.settings_speech_header_title);
            }
            addPreferencesFromResource(R.xml.settings_speech);
        }
    }

    public static class MusicPrefFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ActionBar bar = getActivity().getActionBar();
            if (bar != null) {
                bar.setTitle(R.string.settings_music_header_title);
            }
            addPreferencesFromResource(R.xml.settings_music);
        }
    }

    public static class LightAutomationPrefFragment extends PreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        private String mDisableAutoWifiKey;
        private String mDisableAutoStartupKey;
        private String mDisableAutoSunsetKey;

        private Preference mRegardlessOfUserInputPref;
        private Preference mRestrictToSSIDPref;
        private Preference mHardcodeLocationPref;
        private Preference mAutoTillTimePref;
        private Preference mMaxAutoBrightnessPref;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ActionBar bar = getActivity().getActionBar();
            if (bar != null) {
                bar.setTitle(R.string.settings_light_auto_header_title);
            }
            setHasOptionsMenu(true);

            addPreferencesFromResource(R.xml.settings_automated_lights);

            // Set the empty text for the SSID enter preference
            SummaryEditTextPreference pref = (SummaryEditTextPreference) getPreferenceManager()
                    .findPreference(getString(R.string.setting_light_auto_enter_ssid_key));
            pref.setOnEmptyTextDialogShowListener(new SummaryEditTextPreference.OnEmptyTextDialogShowListener() {
                @Override
                public String OnEmptyTextDialogShow() {
                    WifiManager man = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = man.getConnectionInfo();
                    String ssid = wifiInfo.getSSID();
                    if (ssid.charAt(0) == '<') {
                        return null;
                    }
                    return ssid.substring(1, ssid.length() - 1);
                }
            });

            // Set the dependencies on multiple preference
            mDisableAutoWifiKey = getString(R.string.settings_light_auto_disable_wifi_key);
            mDisableAutoStartupKey = getString(R.string.settings_light_auto_disable_startup_key);
            mDisableAutoSunsetKey = getString(R.string.setting_light_auto_sunset_automation_disable_key);

            mRegardlessOfUserInputPref = findPreference(getString(R.string.setting_light_auto_even_user_interacts_key));
            mRestrictToSSIDPref = findPreference(getString(R.string.setting_light_auto_lock_to_network_key));
            mHardcodeLocationPref = findPreference(getString(R.string.setting_light_location_hardcode_location_key));
            mAutoTillTimePref = findPreference(getString(R.string.settings_light_auto_night_time_key));
            mMaxAutoBrightnessPref = findPreference(getString(R.string.settings_light_auto_max_automated_brightness_key));

            // Set the dependencies enable/disable on startup
            onSharedPreferenceChanged(getPreferenceManager().getSharedPreferences(), null);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            super.onCreateOptionsMenu(menu, inflater);

            final String pageKey = getString(R.string.settings_general_light_enable_lights_key);
            inflater.inflate(R.menu.settings_switch_actionbar_menu, menu);
            Switch pageSwitch = (Switch) menu.findItem(R.id.switchButton).getActionView()
                    .findViewById(R.id.switchForActionBar);
            pageSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    getPreferenceManager().getSharedPreferences().edit()
                            .putBoolean(pageKey, isChecked).apply();
                    setAllEnable(isChecked);
                }
            });
            boolean settingsChecked = getPreferenceManager().getSharedPreferences().getBoolean(pageKey, true);
            pageSwitch.setChecked(settingsChecked);
            setAllEnable(settingsChecked);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
            boolean enableLightAutoPrefs = true;

            boolean isDisableSunsetKeyChecked = pref.getBoolean(mDisableAutoSunsetKey, false);
            boolean isDisableWifiKeyChecked = pref.getBoolean(mDisableAutoWifiKey, false);
            boolean isDisableStartupKeyChecked = pref.getBoolean(mDisableAutoStartupKey, false);

            // Disable specific preferences based on dependencies
            if (isDisableSunsetKeyChecked && isDisableStartupKeyChecked) {
                mRegardlessOfUserInputPref.setEnabled(false);
                enableLightAutoPrefs = !isDisableWifiKeyChecked;
            } else {
                mRegardlessOfUserInputPref.setEnabled(true);
            }

            mRestrictToSSIDPref.setEnabled(enableLightAutoPrefs);
            mHardcodeLocationPref.setEnabled(enableLightAutoPrefs);
            mAutoTillTimePref.setEnabled(enableLightAutoPrefs);
            mMaxAutoBrightnessPref.setEnabled(enableLightAutoPrefs);
        }

        private void setAllEnable(boolean isEnabled) {
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
                getPreferenceScreen().getPreference(i).setEnabled(isEnabled);
            }
        }
    }
}
