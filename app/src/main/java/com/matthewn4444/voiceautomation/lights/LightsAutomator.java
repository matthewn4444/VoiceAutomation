package com.matthewn4444.voiceautomation.lights;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.location.LocationListener;
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.matthewn4444.voiceautomation.LazyPref;
import com.matthewn4444.voiceautomation.LocationHelper;
import com.matthewn4444.voiceautomation.R;

import java.util.Calendar;

public class LightsAutomator implements LocationListener {
    public static final String TAG = "LightsAutomator";
    public static final String ExtraStartTime = "intent.extra.start.time";
    public static final String ExtraIntervalSec = "intent.extra.interval.sec";

    private static final int ResKeyLastLightInteraction = R.string.settings_key_last_light_user_interaction;

    private final Context mCtx;
    private final LocationHelper mLocationHelper;

    private boolean mLightsIsReady;
    private Location mLocation;
    private SunriseSunsetCalculator mCalculator;
    private LightsSpeechCategory.ILightController mController;

    public LightsAutomator(Context ctx) {
        this(ctx, false);
    }

    public LightsAutomator(Context ctx, boolean silent) {
        mCtx = ctx;

        // Setup the light controller
        mController = new LFXController(ctx);
        if (!mController.isAvailable()) {
            mController.setOnConnectionChangedListener(
                    new LightsSpeechCategory.ILightController.OnConnectionChangedListener() {
                        @Override
                        public void onConnectionChanged(int lightsConnected, boolean justConnected) {
                            if (!mLightsIsReady && justConnected && mController.isAvailable()) {
                                // Initial connection with the lights have been made, now we can automate them
                                mLightsIsReady = true;
                                init();
                            }
                        }

                        @Override
                        public void onCommandFinished() {
                        }

                        @Override
                        public void onError(Exception e) {
                        }
                    });
            mController.connect();
        } else {
            mLightsIsReady = true;
            init();
        }

        // Setup location
        mLocationHelper = new LocationHelper(ctx);
        mLocationHelper.queryLocation(this, silent);

        // Record this SSID if connected and not recorded yet
        if (LazyPref.getString(ctx, R.string.settings_key_last_wifi_ssid) == null) {
            String ssid = getCurrentSSID(ctx);
            if (ssid != null) {
                PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(
                        ctx.getString(R.string.settings_key_last_wifi_ssid), ssid);
            }
        }
    }

    protected static void log(Object... txt) {
        String returnStr = "";
        int i = 1;
        int size = txt.length;
        if (size != 0) {
            returnStr = txt[0] == null ? "null" : txt[0].toString();
            for (; i < size; i++) {
                returnStr += ", "
                        + (txt[i] == null ? "null" : txt[i].toString());
            }
        }
        Log.i("lunch", returnStr);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            mLocation = location;

            // If the home location is not enabled, we can put the found location there
            if (!LazyPref.getBool(mCtx, R.string.setting_light_location_home_location_key, false)
                    && location.getLatitude() != 0 && location.getLongitude() != 0) {
                PreferenceManager.getDefaultSharedPreferences(mCtx).edit()
                        .putString(mCtx.getString(R.string.settings_key_home_latitude), Double.toString(location.getLatitude()))
                        .putString(mCtx.getString(R.string.settings_key_home_longitude), Double.toString(location.getLongitude()))
                                .apply();
            }
            init();
        } else {
            Log.v(TAG, mCtx.getString(R.string.location_is_unavailable));
        }
    }

    public static String getCurrentSSID(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            String bssid = wifiInfo.getBSSID();
            if (bssid == null || bssid.equals("00:00:00:00:00:00")) {
                return null;
            }
            String ssid = wifiInfo.getSSID();
            if (ssid.charAt(0) != '<') {        // <unknown ssid>, when internet is not connected
                if (ssid.charAt(0) == '"' && ssid.charAt(ssid.length() - 1) == '"') {
                    return ssid.substring(1, ssid.length() - 1);
                }
                return ssid;
            }
        }
        return null;
    }

    public static void cancelAutomator(Context ctx) {
        AlarmManager alarmMgr = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent in = (PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, LightsAutomatorReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT));
        in.cancel();
        alarmMgr.cancel(in);
    }

    static Pair<Calendar, Calendar> getAutomatedSunsetTimes(Location location) {
        final SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(
                new com.luckycatlabs.sunrisesunset.dto.Location(
                        location.getLatitude(), location.getLongitude()), Calendar.getInstance().getTimeZone());
        Calendar startTime = calculator.getOfficialSunsetCalendarForDate(Calendar.getInstance());
        Calendar endTime = calculator.getCivilSunsetCalendarForDate(Calendar.getInstance());

        // The angle of the sun is 8 degrees from start to end, so use that time diff before sunset
        // to detect when we should start turning the lights on, which is 4 degrees before sunset
        double timeDiffSec = ((endTime.getTimeInMillis() - startTime.getTimeInMillis()) * 1.0f / 1000);
        startTime.add(Calendar.MINUTE, -(int) (timeDiffSec * 2 / 60));
        return Pair.create(startTime, endTime);
    }

    static void scheduleSunsetGradualLightsOn(Context ctx, Location location) {
        Calendar now = Calendar.getInstance();

        boolean alarmIsScheduled = (PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, LightsAutomatorReceiver.class),
                PendingIntent.FLAG_NO_CREATE) != null);

        // Do not schedule another alarm when one is happening today already
        if (alarmIsScheduled) {
            Log.v(TAG, "There has been an alarm already scheduled for today to turn on the lights.");
            return;
        }

        // Calculate the intervals of each brightness step
        Pair<Calendar, Calendar> automationTimes = getAutomatedSunsetTimes(location);
        Calendar startTime = automationTimes.first;
        Calendar endTime = automationTimes.second;

        double timeDiffSec = ((endTime.getTimeInMillis() - startTime.getTimeInMillis()) * 1.0f / 1000);
        int secPerInterval = (int)Math.ceil(timeDiffSec / getScheduleNumberOfIntervals(ctx));

        // Schedule the alarms
        Intent intent = new Intent(ctx, LightsAutomatorReceiver.class);
        intent.putExtra(ExtraStartTime, startTime.getTimeInMillis());
        intent.putExtra(ExtraIntervalSec, secPerInterval);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        Log.v(TAG, "Schedule lights for today from " + startTime.get(Calendar.HOUR_OF_DAY) + ":"
                + startTime.get(Calendar.MINUTE) + " to " + endTime.get(Calendar.HOUR_OF_DAY) + ":"
                + endTime.get(Calendar.MINUTE));

        AlarmManager alarmMgr = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (now.after(startTime)) {
            // If we passed the start time already, start at the next interval
            int nextInterval = calculateInterval(now, startTime.getTimeInMillis(), secPerInterval) + 1;
            alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, startTime.getTimeInMillis()
                    + nextInterval * secPerInterval * 1000, secPerInterval * 1000, alarmIntent);
        } else {
            alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, startTime.getTimeInMillis(),
                    secPerInterval * 1000, alarmIntent);
        }
    }

    static int getScheduleNumberOfIntervals(Context ctx) {
        return LazyPref.getIntDefaultRes(ctx, R.string.setting_light_auto_sunset_automation_step_key,
                R.integer.settings_default_sunset_automation_step);
    }

    static void enableAutomation(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(context.getString(ResKeyLastLightInteraction))
                .apply();
    }

    static void disableAutomation(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putLong(context.getString(ResKeyLastLightInteraction), System.currentTimeMillis())
                .apply();
    }

    static boolean isSunsetAutomationEnabled(Context context) {
        return !LazyPref.getBool(context, R.string.setting_light_auto_sunset_automation_disable_key);
    }

    static boolean isAutomationEnabled(Context context) {
        if (LazyPref.getBool(context, R.string.setting_light_auto_even_user_interacts_key)) {
            return true;
        }

        Calendar now = Calendar.getInstance();

        Calendar lastLightInteraction = Calendar.getInstance();
        long then = LazyPref.getLong(context, ResKeyLastLightInteraction);
        if (then == 0) {
            return true;
        }
        lastLightInteraction.setTimeInMillis(then);

        // Enable if the last iteration was more than 2 hours before now
        int resetIdleTime = LazyPref.getIntDefaultRes(context,
                R.string.setting_light_auto_reset_auto_time_key,
                R.integer.settings_default_reset_automation_idle_time);

        boolean isEnabled = now.getTimeInMillis() - lastLightInteraction.getTimeInMillis() > resetIdleTime * 1000 * 60 * 60;
        if (isEnabled) {
            enableAutomation(context);
        }
        return isEnabled;
    }

    static boolean isAutomationAllowedBySSID(Context ctx) {
        if (!LazyPref.getBool(ctx, R.string.setting_light_auto_lock_to_network_key, false)) {
            // We do not care about restricting lights to SSID, so just allow it through
            return true;
        }
        String savedSSID = LazyPref.getString(ctx, R.string.setting_light_auto_enter_ssid_key);
        if (savedSSID == null) {
            // Since there is no ssid set, then we will ignore it
            return true;
        }

        // Try to match current SSID with settings
        String ssid = getCurrentSSID(ctx);
        return ssid != null && ssid.equals(savedSSID);
    }

    static boolean isInAutomationStateTurnOffLights(Context ctx) {
        // To turn off the lights we need to make sure that:
        // 1. Restrict ssid is on and current ssid is the same
        // 2. Home location is enabled
        // 3. Must be disconnecting wifi, handled by receiver
        // 4. Long and lat home location must not be 0
        log(LazyPref.getBool(ctx, R.string.setting_light_auto_lock_to_network_key)
                , LazyPref.getBool(ctx, R.string.setting_light_location_home_location_key)
                , LazyPref.getString(ctx, R.string.settings_key_last_wifi_ssid)
                , LazyPref.getString(ctx, R.string.setting_light_auto_enter_ssid_key)
                , LazyPref.getString(ctx, R.string.settings_key_last_wifi_ssid)
                 , LazyPref.getString(ctx, R.string.setting_light_auto_enter_ssid_key)
                , LazyPref.getString(ctx, R.string.settings_key_home_latitude) != null
                , LazyPref.getString(ctx, R.string.settings_key_home_longitude) != null);

        String lastSSID = LazyPref.getString(ctx, R.string.settings_key_last_wifi_ssid);
        String autoSSID = LazyPref.getString(ctx, R.string.setting_light_auto_enter_ssid_key);
        return LazyPref.getBool(ctx, R.string.setting_light_auto_lock_to_network_key)
            && LazyPref.getBool(ctx, R.string.setting_light_location_home_location_key)
            && lastSSID != null && autoSSID != null && autoSSID.equals(lastSSID)
            && LazyPref.getString(ctx, R.string.settings_key_home_latitude) != null
            && LazyPref.getString(ctx, R.string.settings_key_home_longitude) != null;
    }

    public void reschedule() {
        cancelAutomator(mCtx);
        if (mLocation != null && isSunsetAutomationEnabled(mCtx)) {
            scheduleSunsetGradualLightsOn(mCtx, mLocation);
        }
    }

    public LightsSpeechCategory.ILightController getLightController() {
        return mController;
    }

    public void retryLocation() {
        mLocationHelper.queryLocation(this);
    }

    public boolean isLocationReady() {
        return mLocation != null;
    }

    public boolean isLightsIsReady() {
        return mLightsIsReady;
    }

    public void onPause() {
        mLocationHelper.onPause();
    }

    public void onResume() {
        mLocationHelper.onResume();
    }


    private void init() {
        if (!mLightsIsReady || mLocation == null) {
            return;
        }

        Calendar now = Calendar.getInstance();
        mCalculator = new SunriseSunsetCalculator(new com.luckycatlabs.sunrisesunset.dto.Location(
                mLocation.getLatitude(), mLocation.getLongitude()), now.getTimeZone());

        // Set the light on startup according to the time of day
        if (mController.isAvailable()
                && !LazyPref.getBool(mCtx, R.string.settings_light_auto_disable_startup_key)
                && isAutomationEnabled(mCtx)
                && LightsAutomator.isAutomationAllowedBySSID(mCtx)) {
            int brightness = autoBrightnessForNow();
            if (brightness == 0) {
                mController.setBrightnessPercentage(0);
                mController.turnOff();
            } else {
                mController.turnOn();
                mController.setBrightnessPercentage(brightness);
            }
        }

        // If we disable the light automation during sunset we should cancel it
        if (isSunsetAutomationEnabled(mCtx)) {
            // Automate the lights to gradually turn on
            if (now.before(calculateNightTime())) {
                scheduleSunsetGradualLightsOn(mCtx, mLocation);
            }
        } else {
            cancelAutomator(mCtx);
        }
    }

    private int autoBrightnessForNow() {
        Pair<Calendar, Calendar> automationTimes = getAutomatedSunsetTimes(mLocation);
        Calendar startSunsetTime = automationTimes.first;
        Calendar endSunsetTime = automationTimes.second;

        Calendar now = Calendar.getInstance();
        Calendar night = calculateNightTime();
        Calendar lateNightTime = getLateNightTime();

        if (now.after(night) || now.before(lateNightTime)) {
            // It is after the night time or before the late night set in settings, so max brightness
            return getMaxBrightness(mCtx);
        } else if (now.before(startSunsetTime)) {
            // It is still daylight, so no brightness
            return 0;
        } else {
            // Calculate the brightness since we are in between the sunset and night time
            long total = endSunsetTime.getTimeInMillis() - startSunsetTime.getTimeInMillis();
            long currently = now.getTimeInMillis() - startSunsetTime.getTimeInMillis();

            return (int)((currently * 1.0f / total) * getMaxBrightness(mCtx));
        }
    }

    private Calendar getLateNightTime() {
        Calendar ret = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        long timeMill = LazyPref.getLong(mCtx, R.string.settings_light_auto_night_time_key);
        if (timeMill == 0) {
            // Set default time
            ret.set(Calendar.HOUR_OF_DAY, mCtx.getResources().getInteger(R.integer.settings_default_last_night_hour));
            ret.set(Calendar.MINUTE, 0);
        } else {
            then.setTimeInMillis(timeMill);
            ret.set(Calendar.HOUR_OF_DAY, then.get(Calendar.HOUR_OF_DAY));
            ret.set(Calendar.MINUTE, then.get(Calendar.MINUTE));
        }
        return ret;
    }

    static int getMaxBrightness(Context ctx) {
        return (int)(100.0f * LazyPref.getFloat(ctx, R.string.settings_light_auto_max_automated_brightness_key, 1));
    }

    static int calculateInterval(Calendar now, long startTime, int secPerInterval) {
        double timePassedFromStart = ((now.getTimeInMillis() - startTime) * 1.0f / 1000);
        return (int)(timePassedFromStart / secPerInterval) + 1;
    }

    private Calendar calculateNightTime() {
        return mCalculator.getCivilSunsetCalendarForDate(Calendar.getInstance());
    }
}
