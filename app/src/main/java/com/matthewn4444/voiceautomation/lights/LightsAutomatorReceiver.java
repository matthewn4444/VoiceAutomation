package com.matthewn4444.voiceautomation.lights;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.matthewn4444.voiceautomation.LazyPref;
import com.matthewn4444.voiceautomation.R;

import java.util.Calendar;

public class LightsAutomatorReceiver extends BroadcastReceiver {
    public static final String TAG = "LightsAutomatorReceiver";

    private static final int DisconnectLaterTimeout = 9000;
    private Handler mHandler = new Handler();

    @Override
    public void onReceive(final Context context, Intent intent) {
        // Since the preference is not enabled, we should cancel and not automate the lights
        // Also cancel if you do not have permissions to location
        if (!LightsAutomator.isSunsetAutomationEnabled(context)
                || !LightsSpeechCategory.areLightsEnabled(context)
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
            LightsAutomator.cancelAutomator(context);
            return;
        }

        if (intent != null) {
            long startMill = intent.getLongExtra(LightsAutomator.ExtraStartTime, 0);
            int secPerInterval = intent.getIntExtra(LightsAutomator.ExtraIntervalSec, 0);

            if (startMill == 0 || secPerInterval == 0) {
                Log.e(TAG, "Received the intent but one of the fields is invalid (Start: "
                        + startMill + ", Interval: " + secPerInterval + ")");
                LightsAutomator.cancelAutomator(context);
                return;
            }

            // Calculate the next brightness
            Calendar now = Calendar.getInstance();

            final int thisIter = LightsAutomator.calculateInterval(now, startMill, secPerInterval);
            final int maxBrightness = LightsAutomator.getMaxBrightness(context);
            final int currentBrightness = Math.min((int) ((thisIter * 1.0f /
                    LightsAutomator.getScheduleNumberOfIntervals(context))
                    * maxBrightness), maxBrightness);

            // In order for the lights to be turned on gradually, we need to make the following
            // conditions are in order:
            // 1. Wifi is on
            // 2. SSID matches where the user has their lights connected to
            // 3. If the user has not yet changed the lights using this app
            // 4. Lights are available

            ConnectivityManager connManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifiController = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            // If the user has interacted with the lights today, then we will not automate the lights
            if (wifiController.isConnected() && LightsAutomator.isAutomationEnabled(context)
                    && LightsAutomator.isAutomationAllowedBySSID(context)) {
                // Connect the lights and set the brightness
                final LFXController lights = new LFXController(context);
                final int slowLightDuration = thisIter == 1 ? 0 : 1000 * LazyPref.getIntDefaultRes(context,
                        R.string.setting_light_auto_sunset_automation_step_duration_key,
                        R.integer.settings_default_sunset_automation_step_duration_sec);
                lights.setOnConnectionChangedListener(
                        new LightsSpeechCategory.ILightController.OnConnectionChangedListener() {
                    @Override
                    public void onConnectionChanged(int lightsConnected, boolean justConnected) {
                        if (lights.isAvailable()) {
                            setLights(lights, currentBrightness, slowLightDuration);
                        } else {
                            lights.disconnect();
                        }
                    }
                });
                lights.connect();
                if (lights.isAvailable()) {
                    setLights(lights, currentBrightness, slowLightDuration);
                }
            }

            // When at the end of the intervals, end the repeated intervals
            if (thisIter >= LightsAutomator.getScheduleNumberOfIntervals(context)) {
                LightsAutomator.cancelAutomator(context);
            }
        } else {
            Log.e(TAG, "Major issue that the intent was not set to the alarm!");
            LightsAutomator.cancelAutomator(context);
        }
    }

    private void setLights(final LFXController lights, int brightnessPercentage, int duration) {
        lights.setBrightnessPercentage(brightnessPercentage, duration);
        lights.turnOn();

        // Disconnect the lights later because if we do it now, it wont change the lights
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                lights.disconnect();
            }
        }, DisconnectLaterTimeout);
    }
}
