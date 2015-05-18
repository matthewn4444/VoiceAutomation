package com.matthewn4444.voiceautomation;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;

import com.matthewn4444.voiceautomation.lights.LFXController;
import com.matthewn4444.voiceautomation.lights.LightsAutomator;
import com.matthewn4444.voiceautomation.lights.LightsSpeechCategory;


public class ListeningActivity extends AppCompatActivity {
    public static final int MAX_LIGHT_BRIGHTNESS = 70;

    private SpeechCategory[] mCategories;
    private SpeechController mController;
    private LightsAutomator mLightAutomator;
    private LightsSpeechCategory.ILightController mLightController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mLightController = new LFXController(this);
        mLightController.setOnConnectionChangedListener(new LightsSpeechCategory.ILightController.OnConnectionChangedListener() {
            @Override
            public void onConnectionChanged(int lightsConnected, boolean justConnected) {
                if (mLightAutomator == null && justConnected) {
                    // Initial connection with the lights have been made, now we can automate them
                    mLightAutomator = new LightsAutomator(ListeningActivity.this, mLightController, MAX_LIGHT_BRIGHTNESS);
                }
            }
        });

        setupSpeechController();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mController.pause();
        if (mLightAutomator != null) {
            mLightAutomator.pause();
        }
        for (SpeechCategory cate: mCategories) {
            cate.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mLightAutomator != null) {
            mLightAutomator.resume();
        }
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

    private void setupSpeechController() {
        UIPresenter presenter = new UIPresenter(this);
        mCategories = new SpeechCategory[]{
                new LightsSpeechCategory(this, presenter, mLightController)
        };
        mController = new SpeechController(this, mCategories);
        mController.setSpeechListener(presenter);
    }
}
