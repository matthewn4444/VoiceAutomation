package com.matthewn4444.voiceautomation.lights;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

import java.util.Calendar;

public class LightsAutomatorReceiver extends BroadcastReceiver {
    public static final String TAG = "LightsAutomatorReceiver";

    private static final int DisconnectLaterTimeout = 5000;
    private static final int SlowLightDuration = 3000;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent != null) {
            long startMill = intent.getLongExtra(LightsAutomator.ExtraStartTime, 0);
            int secPerInterval = intent.getIntExtra(LightsAutomator.ExtraIntervalSec, 0);
            int finalBrightness = intent.getIntExtra(LightsAutomator.ExtraFinalBrightness, 0);

            if (startMill == 0 || secPerInterval == 0 || finalBrightness == 0) {
                Log.e(TAG, "Received the intent but one of the fields is invalid (Start: "
                        + startMill + ", Interval: " + secPerInterval
                        + ", Final: " + finalBrightness + ")");
                return;
            }

            // Calculate the next brightness
            Calendar now = Calendar.getInstance();
            final int thisIter = LightsAutomator.calculateInterval(now, startMill, secPerInterval);
            final int currentBrightness = (int)((thisIter * 1.0f /
                    LightsAutomator.ScheduleNumberOfIntervals) * finalBrightness);

            // In order for the lights to be turned on gradually, we need to make the following
            // conditions are in order:
            // 1. Wifi is on
            // 2. SSID matches where the user has their lights connected to
            // 3. The "auto" flag is on (which means users have not manually adjusted lights)
            // 4. Lights are available
            ConnectivityManager connManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifiController = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            // TODO check if the auto setting exists
            if (wifiController.isConnected()) {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                // TODO check the SSID to match the one in settings once settings is implemented: wifiInfo.getSSID()

                // Connect the lights and set the brightness
                final LFXController lights = new LFXController(context);
                lights.setOnConnectionChangedListener(
                        new LightsSpeechCategory.ILightController.OnConnectionChangedListener() {
                    @Override
                    public void onConnectionChanged(int lightsConnected, boolean justConnected) {
                        setLights(lights, currentBrightness, thisIter == 1 ? 0 : SlowLightDuration);
                    }
                });
                lights.connect();
                if (lights.isAvailable()) {
                    setLights(lights, currentBrightness, thisIter == 1 ? 0 : SlowLightDuration);
                }

                // When at the end of the intervals, end the repeated intervals
                if (thisIter >= LightsAutomator.ScheduleNumberOfIntervals) {
                    LightsAutomator.cancelAutomator(context);
                }
            }
        } else {
            Log.e(TAG, "Major issue that the intent was not set to the alarm!");
        }
    }

    private void setLights(final LFXController lights, int brightnessPercentage, int duration) {
        lights.setBrightnessPercentage(brightnessPercentage, duration);
        lights.turnOn();

        // Disconnect the lights later because if we do it now, it wont change the lights
        Handler a = new Handler();
        a.postDelayed(new Runnable() {
            @Override
            public void run() {
                lights.disconnect();
            }
        }, DisconnectLaterTimeout);
    }
}
