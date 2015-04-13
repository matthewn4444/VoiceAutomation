package com.matthewn4444.voiceautomation;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.WindowManager;

import com.matthewn4444.voiceautomation.lights.LFXController;
import com.matthewn4444.voiceautomation.lights.LightsSpeechCategory;


public class ListeningActivity extends ActionBarActivity {
    private SpeechCategory[] mCategories;
    private SpeechController mController;
    private LightsSpeechCategory.ILightController mLightController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mLightController = new LFXController(this);

        setupSpeechController();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mController.pause();
        for (SpeechCategory cate: mCategories) {
            cate.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
