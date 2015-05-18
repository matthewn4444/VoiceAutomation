package com.matthewn4444.voiceautomation.lights;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;
import com.matthewn4444.voiceautomation.R;

import java.util.Calendar;

import im.delight.android.location.SimpleLocation;

public class LightsAutomator {
    private final Context mCtx;
    private final LightsSpeechCategory.ILightController mController;
    private final int mMaxBrightness;
    private SimpleLocation mLocationObj;
    private SunriseSunsetCalculator mCalculator;

    public LightsAutomator(Context ctx, LightsSpeechCategory.ILightController controller, int maxBrightness) {
        mCtx = ctx;
        mController = controller;
        mMaxBrightness = maxBrightness;
        mLocationObj = new SimpleLocation(mCtx);

        // Since the sunsetApi needs location to get the sunset timing, we need to ask users
        if (!mLocationObj.hasLocationEnabled()) {
            new AlertDialog.Builder(mCtx)
                    .setMessage(R.string.prompt_ask_turn_on_location)
                    .setTitle(R.string.app_name)
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mLocationObj.setListener(new SimpleLocation.Listener() {
                                @Override
                                public void onPositionChanged() {
                                    // Get the first position and we are good
                                    mLocationObj.setListener(null);
                                    gotLocation();
                                }
                            });
                            SimpleLocation.openSettings(mCtx);
                        }
                    })
                    .show();
        } else {
            gotLocation();
        }
    }

    public void pause() {
        if (mLocationObj != null) {
            mLocationObj.endUpdates();
        }
    }

    public void resume() {
        if (mLocationObj != null) {
            mLocationObj.beginUpdates();
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

    private synchronized void gotLocation() {
        Location loc = new Location(mLocationObj.getLatitude(), mLocationObj.getLongitude());
        mCalculator = new SunriseSunsetCalculator(loc, Calendar.getInstance().getTimeZone());
        mLocationObj.endUpdates();
        mLocationObj = null;

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
}
