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

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.matthewn4444.voiceautomation.LazyPref;
import com.matthewn4444.voiceautomation.LocationHelper;
import com.matthewn4444.voiceautomation.R;

import java.util.Calendar;

public class LightsAutomator implements LocationHelper.OnLocationFoundListener {
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
                    });
            mController.connect();
        } else {
            mLightsIsReady = true;
            init();
        }

        // Setup location
        mLocationHelper = new LocationHelper(ctx);
        if (mLocationHelper.hasLocationEnabled() || !silent) {
            mLocationHelper.queryLocation(this);
        } else {
            // Get the last saved location if silent
            Location location = mLocationHelper.getCacheLocation();
            if (location != null) {
                mLocation = location;
                init();
            } else {
                Log.w(TAG, "Connected to wifi but unable to set lights because no location is available.");
            }
        }
    }

    @Override
    public void onLocationFound(Location location) {
        if (location != null) {
            mLocationHelper.cacheLocation();
            mLocation = location;
            init();
        } else {
            Log.v(TAG, mCtx.getString(R.string.location_is_unavailable));
        }
    }

    static void cancelAutomator(Context ctx) {
        AlarmManager alarmMgr = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent in = (PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, LightsAutomatorReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT));
        in.cancel();
        alarmMgr.cancel(in);
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
        final SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(
                new com.luckycatlabs.sunrisesunset.dto.Location(
                        location.getLatitude(), location.getLongitude()), now.getTimeZone());
        Calendar startTime = calculator.getOfficialSunsetCalendarForDate(Calendar.getInstance());
        Calendar endTime = calculator.getCivilSunsetCalendarForDate(Calendar.getInstance());

        // The angle of the sun is 4 degrees from start to end, so use that time diff before sunset
        // to detect when we should start turning the lights on, which is 4 degrees before sunset
        double timeDiffSec = ((endTime.getTimeInMillis() - startTime.getTimeInMillis()) * 1.0f / 1000);
        startTime.add(Calendar.MINUTE, -(int) (timeDiffSec / 60));

        timeDiffSec = ((endTime.getTimeInMillis() - startTime.getTimeInMillis()) * 1.0f / 1000);
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
        boolean isEnabled = lastLightInteraction.get(Calendar.YEAR) != now.get(Calendar.YEAR)
                ||  lastLightInteraction.get(Calendar.MONTH) != now.get(Calendar.MONTH)
                || lastLightInteraction.get(Calendar.DAY_OF_MONTH) != now.get(Calendar.DAY_OF_MONTH);
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
        WifiManager wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID();
        if (ssid.charAt(0) == '<') {        // <unknown ssid>, when internet is not connected
            return false;
        }
        return ssid.substring(1, ssid.length() - 1).equals(savedSSID);
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
        Calendar now = Calendar.getInstance();
        Calendar sunset = calculateSunsetTime();
        Calendar night = calculateNightTime();
        Calendar lateNightTime = getLateNightTime();

        if (now.after(night) || now.before(lateNightTime)) {
            // It is after the night time or before the late night set in settings, so max brightness
            return getMaxBrightness(mCtx);
        } else if (now.before(sunset)) {
            // It is still daylight, so no brightness
            return 0;
        } else {
            // Calculate the brightness since we are in between the sunset and night time
            long total = night.getTimeInMillis() - sunset.getTimeInMillis();
            long currently = now.getTimeInMillis() - sunset.getTimeInMillis();
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

    private Calendar calculateSunsetTime() {
        return mCalculator.getOfficialSunsetCalendarForDate(Calendar.getInstance());
    }

    private Calendar calculateNightTime() {
        return mCalculator.getCivilSunsetCalendarForDate(Calendar.getInstance());
    }
}
