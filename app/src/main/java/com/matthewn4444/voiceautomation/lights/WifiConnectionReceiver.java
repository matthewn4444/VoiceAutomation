package com.matthewn4444.voiceautomation.lights;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.matthewn4444.voiceautomation.LocationHelper;
import com.matthewn4444.voiceautomation.R;

import java.util.Calendar;

public class WifiConnectionReceiver extends BroadcastReceiver {
    public static final String TAG = "WifiConnectionReceiver";

    private static final int DelayActionMill = 1000;    // Delayed actions for some moments
    private static final String UnknownSSID = "<unknown ssid>";
    private SharedPreferences mPref;
    private String mLastDisconnectionSettingsKey;

    private static final int LastConnectionThresholdSec = 1;   // 1 sec to avoid multiple events
    private static final int StartAutomationThresholdMin = 10; // Start automation if wifi was off for more than 10 min and then back on

    @Override
    public void onReceive(Context context, Intent intent) {
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

    private void locationReadyAndConnected(final Context context, final Location location) {
        final LightsSpeechCategory.ILightController controller = new LFXController(context);
        if (controller.isAvailable()) {
            locationLightsReadyAndConnected(context, location, controller);
        } else {
            controller.setOnConnectionChangedListener(new LightsSpeechCategory.ILightController.OnConnectionChangedListener() {
                @Override
                public void onConnectionChanged(int lightsConnected, boolean justConnected) {
                    if (justConnected) {
                        // Now that location, lights and wifi is connected, we can turn the lights on
                        // If unavailable then try again 1 sec later, then give up if fails again
                        if (!controller.isAvailable()) {
                            Log.w(TAG, "Light controller is not ready to show lights yet, wait another " + DelayActionMill + " ms.");
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (controller.isAvailable()) {
                                        locationLightsReadyAndConnected(context, location, controller);
                                    } else {
                                        Log.w(TAG, "Light controller is not ready for the second time for automation, give up.");
                                        controller.disconnect();
                                    }
                                }
                            }, DelayActionMill);
                        } else {
                            locationLightsReadyAndConnected(context, location, controller);
                        }
                    }
                }
            });
            controller.connect();
        }
    }

    private void locationLightsReadyAndConnected(final Context context, Location location,
                                                 final LightsSpeechCategory.ILightController controller) {
        // Automate the lights
        new LightsAutomator(context, location, controller);

        // Safe way to make sure that the lights are adjusted correctly due to network latency
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                controller.disconnect();
            }
        }, DelayActionMill * 4);
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

                LocationHelper locationHelper = new LocationHelper(context);
                if (locationHelper.hasLocationEnabled()) {
                    locationHelper.queryLocation(new LocationHelper.OnLocationFoundListener() {
                        @Override
                        public void onLocationFound(Location location) {
                            locationReadyAndConnected(context, location);
                        }
                    });
                } else {
                    // Get the last saved location :(
                    Location location = locationHelper.getCacheLocation();
                    if (location != null) {
                        locationReadyAndConnected(context, location);
                    } else {
                        Log.w(TAG, "Connected to wifi but unable to set lights because no location is available.");
                    }
                }
            }
        }
    }

    private void onDisconnect(Context context, Intent intent) {
        mPref.edit()
                .putLong(mLastDisconnectionSettingsKey, Calendar.getInstance().getTimeInMillis())
                .apply();
    }
}
