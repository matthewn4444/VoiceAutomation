package com.matthewn4444.voiceautomation.lights;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;
import com.google.android.gms.location.LocationListener;
import com.matthewn4444.voiceautomation.LazyPref;
import com.matthewn4444.voiceautomation.LocationHelper;
import com.matthewn4444.voiceautomation.R;

public class LightsOffService extends GcmTaskService {
    private static final String TAG = "LightsOffService";
    private static final float DistanceThreshold = 0.00035f;
    private static final long MinExecutionTimeSec = 5 * 60;             // 5 minutes
    private static final long MaxExecutionTimeSec = 20 * 60;            // 20 minutes
    private static final long MaxIterations = 10;
    private static final long TimeElapsedGiveUp = 1000 * 60 * 60 * 5;   // 5 hours
    private static final String IterationExtra = "iteration.extra";
    private static final String StartTimeExtra = "start.time.extra";
    private static final String LocationVerifiedExtra = "location.verified.extra";

    /**
     * Cancel any pending tasks that turns off the lights
     * @param ctx Context
     */
    static void cancelAll(Context ctx) {
        GcmNetworkManager.getInstance(ctx).cancelAllTasks(LightsOffService.class);
    }

    /**
     * Call this to try to turn off the lights if allowed. If unable because of a condition below
     * has failed, this will reschedule the task for later.
     * The criteria for that are:
     * - must have geolocation set to fixed point
     * - must have hardcoded ssid
     * - current location must be far enough from fixed point
     * @param ctx Context
     * @return GcmNetworkManager success or fail
     */
    static int turnOffLightsIfAllowed(final Context ctx) {
        return turnOffLightsIfAllowed(ctx, 0, false, 0);
    }

    private static void turnOffLights(final Context ctx, final int iteration, final long startTime) {
        // Check if we can access the internet
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo == null || !netInfo.isConnectedOrConnecting()) {
            // No connection available, we should schedule a job for later
            Log.d(TAG, "No internet now, we should reschedule for later");
            schedule(ctx, iteration + 1, true, startTime);
        } else {
            final LFXController lights = new LFXController(ctx);
            lights.setOnConnectionChangedListener(new LightsSpeechCategory.ILightController.OnConnectionChangedListener() {
                @Override
                public void onConnectionChanged(int lightsConnected, boolean justConnected) {
                    if (lightsConnected == 0) {
                        Log.w(TAG, "Cannot turn off lights because there are none");
                        lights.disconnect();
                    }
                }

                @Override
                public void onCommandFinished() {
                    // Check if it is on
                    if (lights.getBrightnessPercentage() == 0 || !lights.isOn()) {
                        lights.disconnect();

                        // Remove the last ssid and last time disconnected so that when connected again
                        // it will automatically turn on lights when dark
                        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                                .remove(ctx.getString(R.string.settings_key_last_wifi_ssid))
                                .remove(ctx.getString(R.string.settings_key_last_wifi_time_disconnection))
                                .apply();
                    } else {
                        if (!LazyPref.getBool(ctx, R.string.settings_light_auto_disable_off_leave_key)) {
                            lights.turnOff();
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    // Reschedule error
                    Log.d(TAG, "Error, reschedule for later: " + e.getMessage());
                    schedule(ctx, iteration + 1, true, startTime);
                }
            });
            lights.connect();
        }
    }

    private static int turnOffLightsIfAllowed(final Context ctx, final int iteration, final boolean locationVerified, final long startTime) {
        if (locationVerified) {
            // Already verified, just turn off lights
            turnOffLights(ctx, iteration, startTime);
            return GcmNetworkManager.RESULT_SUCCESS;
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        String latStr = pref.getString(ctx.getString(R.string.settings_key_home_latitude), null);
        String longStr = pref.getString(ctx.getString(R.string.settings_key_home_longitude), null);
        if (latStr == null || longStr == null) {
            Log.e(TAG, "At wierd state, somehow this was allowed through and lat long was not checked!!");
            return GcmNetworkManager.RESULT_FAILURE;
        }
        final double latitude = Double.valueOf(latStr);
        final double longitude = Double.valueOf(longStr);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                LocationHelper helper = new LocationHelper(ctx, true);
                helper.queryLocation(new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        if (location != null) {
                            final double distance = Math.sqrt(Math.pow(location.getLatitude() - latitude, 2) + Math.pow(location.getLongitude() - longitude, 2));
                            if (distance > DistanceThreshold) {

                                // When wifi disconnects and hands off to data, it will take some time
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        turnOffLights(ctx, iteration, startTime);
                                    }
                                }, 5000);
                                return;
                            }
                        }
                        Log.w(TAG, "Schedule a new task");
                        schedule(ctx, iteration + 1, false, startTime);
                    }
                }, true);
            }
        });
        return GcmNetworkManager.RESULT_SUCCESS;
    }

    private static void schedule(final Context ctx, final int iteration, final boolean locationVerified, final long startTime) {
        Bundle bundle = new Bundle();
        bundle.putInt(IterationExtra, iteration);
        bundle.putBoolean(LocationVerifiedExtra, locationVerified);
        if (iteration == 0) {
            bundle.putLong(StartTimeExtra, System.currentTimeMillis());
        } else {
            bundle.putLong(StartTimeExtra, startTime);
        }
        OneoffTask task = new OneoffTask.Builder()
                .setService(LightsOffService.class)
                .setExecutionWindow(MinExecutionTimeSec, MaxExecutionTimeSec)
                .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                .setUpdateCurrent(true)
                .setTag(TAG)
                .setExtras(bundle)
                .build();
        GcmNetworkManager.getInstance(ctx).schedule(task);
    }

    @Override
    public int onRunTask(TaskParams taskParams) {
        Bundle bundle = taskParams.getExtras();
        int iteration = bundle != null ? bundle.getInt(IterationExtra, 0) : 0;
        boolean verified = bundle != null && bundle.getBoolean(LocationVerifiedExtra, false);
        long startTime = bundle != null ? bundle.getLong(StartTimeExtra, System.currentTimeMillis()) : System.currentTimeMillis();
        if (System.currentTimeMillis() - startTime > TimeElapsedGiveUp) {
            Log.v(TAG, "Past the time elapsed, time to give up on turning off lights");
            return GcmNetworkManager.RESULT_SUCCESS;
        }
        if (iteration > MaxIterations) {
            Log.w(TAG, "Ran max number of iteration and now it will give up");
            return GcmNetworkManager.RESULT_FAILURE;
        }
        Log.d(TAG, "Running lights off service, iteration = " + iteration + ", verified: " + verified);
        return turnOffLightsIfAllowed(getApplicationContext(), iteration, verified, startTime);
    }
}
