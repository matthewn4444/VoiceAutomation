package com.matthewn4444.voiceautomation.lights;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import java.util.Calendar;

public class LightsAutomator {
    public static final String TAG = "LightsAutomator";
    public static final int ScheduleNumberOfIntervals = 10;
    public static final String ExtraStartTime = "intent.extra.start.time";
    public static final String ExtraIntervalSec = "intent.extra.interval.sec";
    public static final String ExtraFinalBrightness = "intent.extra.final.brightness";

    private final Context mCtx;
    private final LightsSpeechCategory.ILightController mController;
    private final int mMaxBrightness;
    private final Location mLocation;
    private SunriseSunsetCalculator mCalculator;

    public LightsAutomator(Context ctx, Location location, LightsSpeechCategory.ILightController controller, int maxBrightness) {
        mCtx = ctx;
        mLocation = location;
        mController = controller;
        mMaxBrightness = maxBrightness;

        init();
    }

    static void cancelAutomator(Context ctx) {
        AlarmManager alarmMgr = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent in = (PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, LightsAutomatorReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT));
        in.cancel();
        alarmMgr.cancel(in);
    }

    static void scheduleSunsetGradualLightsOn(Context ctx, Location location, int maxBrightness) {
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
        int secPerInterval = (int)Math.ceil(timeDiffSec / ScheduleNumberOfIntervals);

        // Schedule the alarms
        Intent intent = new Intent(ctx, LightsAutomatorReceiver.class);
        intent.putExtra(ExtraStartTime, startTime.getTimeInMillis());
        intent.putExtra(ExtraIntervalSec, secPerInterval);
        intent.putExtra(ExtraFinalBrightness, maxBrightness);
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

    private void init() {
        Calendar now = Calendar.getInstance();
        mCalculator = new SunriseSunsetCalculator(new com.luckycatlabs.sunrisesunset.dto.Location(
                mLocation.getLatitude(), mLocation.getLongitude()), now.getTimeZone());

        // Set the light on startup according to the time of day
        if (mController.isAvailable()) {
            // TODO detect if we should be off auto for the day
            int brightness = autoBrightnessForNow();
            if (brightness == 0) {
                mController.setBrightnessPercentage(0);
                mController.turnOff();
            } else {
                mController.turnOn();
                mController.setBrightnessPercentage(brightness);
            }
        }

        // Automate the lights to gradually turn on
        if (now.before(calculateNightTime())) {
            scheduleSunsetGradualLightsOn(mCtx, mLocation, mMaxBrightness);
        }
    }

    private int autoBrightnessForNow() {
        Calendar now = Calendar.getInstance();
        Calendar sunset = calculateSunsetTime();
        Calendar night = calculateNightTime();

        if (now.after(night)) {
            // It is after the night time, so max brightness
            return mMaxBrightness;
        } else if (now.before(sunset)) {
            // It is still daylight, so no brightness
            return 0;
        } else {
            // Calculate the brightness since we are in between the sunset and night time
            long total = night.getTimeInMillis() - sunset.getTimeInMillis();
            long currently = now.getTimeInMillis() - sunset.getTimeInMillis();
            return (int)((currently * 1.0f / total) * mMaxBrightness);
        }
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
