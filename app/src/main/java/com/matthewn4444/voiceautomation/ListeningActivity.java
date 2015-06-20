package com.matthewn4444.voiceautomation;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

import com.matthewn4444.voiceautomation.lights.LightsAutomator;
import com.matthewn4444.voiceautomation.lights.LightsSpeechCategory;
import com.matthewn4444.voiceautomation.music.MusicSpeechCategory;
import com.matthewn4444.voiceautomation.settings.Settings;

import java.util.ArrayList;
import java.util.List;


public class ListeningActivity extends AppCompatActivity implements
        View.OnClickListener {
    private static final int REQUEST_CODE_SETTINGS = 1;
    private static final int SettingsButtonId = R.id.settings_button;

    private UIPresenter mPresenter;
    private List<SpeechCategory> mCategories = new ArrayList<>();
    private SpeechController mController;
    private LightsAutomator mLightAutomator;

    // Track settings so we know what changed
    private boolean mEnableLights;
    private int mSettingsLastSunsetSteps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        findViewById(SettingsButtonId).setOnClickListener(this);

        mEnableLights = LightsSpeechCategory.areLightsEnabled(this);
        if (mEnableLights) {
            mLightAutomator = new LightsAutomator(this);
        }

        mPresenter = new UIPresenter(this);
        setupSpeechController();
    }

    @Override
    protected void onPause() {
        mController.pause();
        if (mLightAutomator != null) {
            mLightAutomator.onPause();
        }
        mPresenter.onPause();
        mPresenter.immediatelyHideCategory();
        for (SpeechCategory cate: mCategories) {
            cate.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mLightAutomator != null) {
            mLightAutomator.onResume();
        }
        mPresenter.onResume();
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

            // Check if user has enabled/disabled lights
            boolean newEnableLights = LightsSpeechCategory.areLightsEnabled(this);
            if (newEnableLights != mEnableLights) {
                if (newEnableLights) {
                    // Just enabled the lights
                    mLightAutomator = new LightsAutomator(this);
                } else {
                    // Just disabled the lights
                    LightsAutomator.cancelAutomator(this);
                    mLightAutomator.getLightController().disconnect();
                    mLightAutomator.onPause();
                    mLightAutomator = null;
                }
                speechControllerNeedsReset = true;
                mEnableLights = newEnableLights;
            }

            if (mLightAutomator != null) {
                // Check for changes in Sunset Automation Steps, then we need to reset the scheduling
                int newSunsetSteps = LazyPref.getIntDefaultRes(this,
                        R.string.setting_light_auto_sunset_automation_step_key,
                        R.integer.settings_default_sunset_automation_step);
                if (newSunsetSteps != mSettingsLastSunsetSteps) {
                    mLightAutomator.reschedule();
                }

                // If location was not available before because was not on and then went to settings to
                // hardcode the location, then we should try again with new location
                if (!mLightAutomator.isLocationReady() && LocationHelper.isLocationHardcoded(this)) {
                    mLightAutomator.retryLocation();
                }
            }

            if (speechControllerNeedsReset) {
                mPresenter.speechHasReset();
                mController.shutdown();
                setupSpeechController();
            }
        }
    }

    private void setupSpeechController() {
        mCategories.clear();
        if (mLightAutomator != null) {
            mCategories.add(new LightsSpeechCategory(this, mLightAutomator.getLightController()));
        }
        mCategories.add(new MusicSpeechCategory(this));
        mController = new SpeechController(this, mCategories);
        mController.setSpeechListener(mPresenter);
    }
}
