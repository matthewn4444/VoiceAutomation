package com.matthewn4444.voiceautomation.lights;

import android.content.Context;
import android.location.Location;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import java.util.Calendar;

public class LightsAutomator {
    private final Context mCtx;
    private final LightsSpeechCategory.ILightController mController;
    private final int mMaxBrightness;
    private final Location mLocation;
    private SunriseSunsetCalculator mCalculator;
    private boolean mLookingForLocation = false;

    public LightsAutomator(Context ctx, Location location, LightsSpeechCategory.ILightController controller, int maxBrightness) {
        mCtx = ctx;
        mLocation = location;
        mController = controller;
        mMaxBrightness = maxBrightness;

        init();
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
                mController.turnOff();
            } else {
                mController.turnOn();
                mController.setBrightnessPercentage(brightness);
            }
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

    private Calendar calculateSunsetTime() {
        return mCalculator.getOfficialSunsetCalendarForDate(Calendar.getInstance());
    }

    private Calendar calculateNightTime() {
        return mCalculator.getCivilSunsetCalendarForDate(Calendar.getInstance());
    }
}
