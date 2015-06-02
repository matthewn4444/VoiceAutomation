package com.matthewn4444.voiceautomation;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.matthewn4444.voiceautomation.lights.LFXController;
import com.matthewn4444.voiceautomation.lights.LightsAutomator;
import com.matthewn4444.voiceautomation.lights.LightsSpeechCategory;
import com.matthewn4444.voiceautomation.settings.Settings;


public class ListeningActivity extends AppCompatActivity implements
        View.OnClickListener,
        LocationHelper.OnLocationFoundListener {
    private static final int REQUEST_CODE_SETTINGS = 1;
    private static final int SettingsButtonId = R.id.settings_button;

    private UIPresenter mPresenter;
    private SpeechCategory[] mCategories;
    private SpeechController mController;
    private LightsAutomator mLightAutomator;
    private LocationHelper mLocationHelper;
    private LightsSpeechCategory.ILightController mLightController;
    private int mSettingsLastSunsetSteps;

    private boolean mLocationIsReady = false;
    private boolean mLightsIsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        findViewById(SettingsButtonId).setOnClickListener(this);

        setupLightController();
        mPresenter = new UIPresenter(this);
        setupSpeechController();

        mLocationHelper = new LocationHelper(this);
        mLocationHelper.queryLocation(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLocationHelper.onPause();
        mController.pause();
        mPresenter.immediatelyHideCategory();
        for (SpeechCategory cate: mCategories) {
            cate.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLocationHelper.onResume();
        mController.resume();
        for (SpeechCategory cate: mCategories) {
            cate.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mController.shutdown();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case SettingsButtonId:
                Intent in = new Intent(this, Settings.class);
                mSettingsLastSunsetSteps = LazyPref.getIntDefaultRes(this,
                        R.string.setting_light_auto_sunset_automation_step_key,
                        R.integer.settings_default_sunset_automation_step);
                startActivityForResult(in, REQUEST_CODE_SETTINGS);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SETTINGS) {
            // Check if any of the speech categories has changed their commands
            boolean speechControllerNeedsReset = false;
            for (SpeechCategory category : mCategories) {
                if (category.updateAndHasActivationCommand()) {
                    speechControllerNeedsReset = true;
                }
            }

            // Check for changes in Sunset Automation Steps, then we need to reset the scheduling
            int newSunsetSteps = LazyPref.getIntDefaultRes(this,
                    R.string.setting_light_auto_sunset_automation_step_key,
                    R.integer.settings_default_sunset_automation_step);
            if (newSunsetSteps != mSettingsLastSunsetSteps) {
                mLightAutomator.reschedule();
            }

            // If location was not available before because was not on and then went to settings to
            // hardcode the location, then we should try again with new location
            if (!mLocationIsReady && mLocationHelper.isLocationHardcoded() && mLightAutomator == null) {
                mLocationHelper.queryLocation(this);
            }

            if (speechControllerNeedsReset) {
                mPresenter.speechHasReset();
                mController.shutdown();
                setupSpeechController();
            }
        }
    }

    private void setupSpeechController() {
        mCategories = new SpeechCategory[]{
                new LightsSpeechCategory(this, mPresenter, mLightController)
        };
        mController = new SpeechController(this, mCategories);
        mController.setSpeechListener(mPresenter);
    }

    private void setupLightController() {
        mLightController = new LFXController(this);
        if (!mLightController.isAvailable()) {
            mLightController.setOnConnectionChangedListener(
                    new LightsSpeechCategory.ILightController.OnConnectionChangedListener() {
                @Override
                public void onConnectionChanged(int lightsConnected, boolean justConnected) {
                    if (!mLightsIsReady && justConnected && mLightController.isAvailable()) {
                        // Initial connection with the lights have been made, now we can automate them
                        mLightsIsReady = true;
                        startLightAutomation();
                    }
                }
            });
            mLightController.connect();
        } else {
            mLightsIsReady = true;
            startLightAutomation();
        }
    }

    private void startLightAutomation() {
        if (mLightsIsReady && mLocationIsReady) {
            mLightAutomator = new LightsAutomator(ListeningActivity.this,
                    mLocationHelper.getLastLocation(), mLightController);
        }
    }

    @Override
    public void onLocationFound(Location location) {
        if (location != null) {
            mLocationHelper.cacheLocation();
            mLocationIsReady = true;
            startLightAutomation();
        } else {
            Toast.makeText(this, R.string.location_is_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}
