package com.matthewn4444.voiceautomation;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

import com.matthewn4444.voiceautomation.lights.LightsAutomator;
import com.matthewn4444.voiceautomation.lights.LightsSpeechCategory;
import com.matthewn4444.voiceautomation.music.MusicSpeechCategory;
import com.matthewn4444.voiceautomation.settings.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ListeningActivity extends AppCompatActivity implements
        View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private static final int REQUEST_CODE_SETTINGS = 1;
    private static final int SettingsButtonId = R.id.settings_button;

    private static final String[] NeededPermissions = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

    private UIPresenter mPresenter;
    private HashMap<String, SpeechCategory> mCategories = new HashMap<>();
    private SpeechController mController;
    private LightsAutomator mLightAutomator;
    private MusicSpeechCategory mMusicController;

    // Track settings so we know what changed
    private boolean mEnableLights;
    private int mSettingsLastSunsetSteps;
    private String mLifxRemoteToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        findViewById(SettingsButtonId).setOnClickListener(this);
        mPresenter = new UIPresenter(this, mCategories);
        checkForPermissions();

        mLifxRemoteToken = LazyPref.getString(this, R.string.settings_general_light_lifx_remote_token_key);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mPresenter != null) {
            mPresenter.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onPause() {
        if (mController != null) {
            mController.pause(true);
        }
        if (mLightAutomator != null) {
            mLightAutomator.onPause();
        }
        if (mPresenter != null) {
            mPresenter.onPause();
            mPresenter.immediatelyHideCategory();
        }
        for (String key: mCategories.keySet()) {
            mCategories.get(key).pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Add immersive mode
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 14) {
            uiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        if (Build.VERSION.SDK_INT >= 19) {
            uiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);

        if (mLightAutomator != null) {
            mLightAutomator.onResume();
        }
        if (mPresenter != null) {
            mPresenter.onResume();
        }
        if (mController != null) {
            mController.resume();
        }
        for (String key: mCategories.keySet()) {
            mCategories.get(key).resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mController != null) {
            mController.shutdown();
        }
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
            String newToken = LazyPref.getString(this, R.string.settings_general_light_lifx_remote_token_key);
            boolean newEnableLights = LightsSpeechCategory.areLightsEnabled(this);
            if (newEnableLights != mEnableLights || (newToken != null && !newToken.equals(mLifxRemoteToken))) {
                if (newEnableLights && newToken != null) {      // Temp till local is hacked
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
            }

            if (speechControllerNeedsReset) {
                setupCategories();
                setupSpeechController();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        setupCategories();
        setupSpeechController();
    }

    private void setupLights() {
        if (hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            mEnableLights = LightsSpeechCategory.areLightsEnabled(this);
            if (mEnableLights && mLightAutomator == null) {
                mLightAutomator = new LightsAutomator(this);
                addCategory(new LightsSpeechCategory(this, mLightAutomator.getLightController()));
            }
        }
    }

    private void setupMusic() {
        if (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            if (mMusicController == null) {
                mMusicController = new MusicSpeechCategory(this);
                addCategory(mMusicController);
            }
        }
    }

    private void checkForPermissions() {
        final List<String> requestPermissions = new ArrayList<>();
        for (String permission: NeededPermissions) {
            if (!hasPermission(permission)) {
                requestPermissions.add(permission);
            }
        }
        if (!requestPermissions.isEmpty()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, requestPermissions.get(0))) {
                new AlertDialog.Builder(this)
                        .setCancelable(false )
                        .setMessage(R.string.permissions_requested)
                        .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(ListeningActivity.this,
                                        requestPermissions.toArray(new String[requestPermissions.size()]), 1);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, requestPermissions.toArray(new String[requestPermissions.size()]), 1);
            }
        }
        setupCategories();
        setupSpeechController();
    }

    private void setupSpeechController() {
        mPresenter.speechHasReset(mCategories);
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            if (mController != null) {
                mController.shutdown();
            }
            mController = new SpeechController(this, mCategories);
            mController.setSpeechListener(mPresenter);
        }
    }

    private void setupCategories() {
        setupLights();
        setupMusic();
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void addCategory(SpeechCategory category) {
        mCategories.put(category.getActivationCommand(), category);
    }
}
