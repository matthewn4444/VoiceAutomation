package com.matthewn4444.voiceautomation.lights;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;

import com.matthewn4444.voiceautomation.R;

import java.util.Calendar;

public class WifiConnectionReceiver extends BroadcastReceiver {
    private static final int LightsDisconnectTimeout = 8000;    // Disconnect 8 sec after connected
    private SharedPreferences mPref;
    private String mLastDisconnectionSettingsKey;
    private Handler mHandler = new Handler();

    private static final int LastConnectionThresholdSec = 1;   // 1 sec to avoid multiple events
    private static final int StartAutomationThresholdMin = 10; // Start automation if wifi was off for more than 10 min and then back on

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!LightsSpeechCategory.areLightsEnabled(context)
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mPref = PreferenceManager.getDefaultSharedPreferences(context);
        mLastDisconnectionSettingsKey = context.getString(R.string.settings_key_last_wifi_time_disconnection);

        // Detect between connect and disconnect, only fire the first event
        if(intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if(info.isConnected()) {
                // Wifi is connected, but it sends it more than once, so we need to ignore the others
                long now = System.currentTimeMillis();
                String spamConnectionKey = context.getString(R.string.settings_key_last_spammed_wifi_time_connection);
                long lastConnection = mPref.getLong(spamConnectionKey, 0);
                if ((now - lastConnection) > LastConnectionThresholdSec * 1000) {
                    onConnection(context, intent);
                }
                mPref.edit().putLong(spamConnectionKey, now).apply();
            }
        } else if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if(info.getType() == ConnectivityManager.TYPE_WIFI && !info.isConnected()) {
                // Wifi is disconnected, but it sends it more than once, so we need to ignore the others
                long now = System.currentTimeMillis();
                String spamDisconnectionKey = context.getString(R.string.settings_key_last_wifi_time_disconnection);
                long lastDisconnection = mPref.getLong(spamDisconnectionKey, 0);
                if ((now - lastDisconnection) > LastConnectionThresholdSec * 1000) {
                    onDisconnect(context, intent);
                }
                mPref.edit().putLong(spamDisconnectionKey, now).apply();
            }
        }
    }

    private void onConnection(final Context context, Intent intent) {
        if (LightsAutomator.isAutomationAllowedBySSID(context)) {
            // Once the wifi was off for more than 10 min and then connected, we can start getting
            // a location and start the light automation
            Calendar now = Calendar.getInstance();
            long disconnection = mPref.getLong(mLastDisconnectionSettingsKey, 0);
            if ((now.getTimeInMillis() - disconnection) > StartAutomationThresholdMin * 60 * 1000) {
                // Remove the pref that dictates the user has interacted today and set automation back to auto
                LightsAutomator.enableAutomation(context);

                // Check if light automation upon wifi is disabled
                if (mPref.getBoolean(context.getString(R.string.settings_light_auto_disable_wifi_key), false)) {
                    return;
                }

                // Automate the lights on startup
                final LightsAutomator automator = new LightsAutomator(context, true);

                // Safe way to make sure that the lights are adjusted correctly due to network latency
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        automator.getLightController().disconnect();
                    }
                }, LightsDisconnectTimeout);
            }
        }
    }

    private void onDisconnect(Context context, Intent intent) {
        mPref.edit()
                .putLong(mLastDisconnectionSettingsKey, Calendar.getInstance().getTimeInMillis())
                .apply();
    }
}
