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

import java.util.HashMap;


public class ListeningActivity extends AppCompatActivity implements
        View.OnClickListener {
    private static final int REQUEST_CODE_SETTINGS = 1;
    private static final int SettingsButtonId = R.id.settings_button;

    private UIPresenter mPresenter;
    private HashMap<String, SpeechCategory> mCategories = new HashMap<>();
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

        setupCategories();
        mPresenter = new UIPresenter(this, mCategories);
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
        for (String key: mCategories.keySet()) {
            mCategories.get(key).pause();
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
        for (String key: mCategories.keySet()) {
            mCategories.get(key).resume();
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
            for (String key : mCategories.keySet()) {
                SpeechCategory cate = mCategories.get(key);
                String oldCommand = cate.getActivationCommand();
                if (cate.updateAndHasActivationCommand()) {
                    // Since the command changed, change the key to the new command
                    mCategories.remove(oldCommand);
                    mCategories.put(cate.getActivationCommand(), cate);
                    speechControllerNeedsReset = true;
                }
            }

            // Check if user has enabled/disabled lights
            boolean newEnableLights = LightsSpeechCategory.areLightsEnabled(this);
            if (newEnableLights != mEnableLights) {
                if (newEnableLights) {
                    // Just enabled the lights
                    mLightAutomator = new LightsAutomator(this);

                    // TODO this is a temp thing, make this smarter, wrap each category to control settings
                    addCategory(new LightsSpeechCategory(this, mLightAutomator.getLightController()));
                } else {
                    // Just disabled the lights
                    LightsAutomator.cancelAutomator(this);
                    mLightAutomator.getLightController().disconnect();
                    mLightAutomator.onPause();
                    mLightAutomator = null;

                    // TODO make this smarter
                    String lightsCommand = LazyPref.getStringDefaultRes(this,
                            R.string.settings_general_light_activation_command_key,
                            R.string.settings_default_activation_command_lights);
                    mCategories.remove(lightsCommand);
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
                mController.shutdown();
                setupCategories();
                setupSpeechController();
            }
        }
    }

    private void setupSpeechController() {
        mPresenter.speechHasReset(mCategories);
        mController = new SpeechController(this, mCategories);
        mController.setSpeechListener(mPresenter);
    }

    private void setupCategories() {
        if (mCategories.isEmpty()) {
            if (mLightAutomator != null) {
                addCategory(new LightsSpeechCategory(this, mLightAutomator.getLightController()));
            }
            addCategory(new MusicSpeechCategory(this));
        }
    }

    private void addCategory(SpeechCategory category) {
        mCategories.put(category.getActivationCommand(), category);
    }
}
