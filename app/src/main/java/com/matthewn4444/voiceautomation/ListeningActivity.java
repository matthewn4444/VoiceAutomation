package com.matthewn4444.voiceautomation;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.widget.TextView;
import android.widget.Toast;

import com.matthewn4444.voiceautomation.lights.LFXController;
import com.matthewn4444.voiceautomation.lights.LightsSpeechCategory;


public class ListeningActivity extends ActionBarActivity {
    private SpeechCategory[] mCategories;
    private SpeechController mController;
    private LightsSpeechCategory.ILightController mLightController;

    private TextView mCaptionField;
    private TextView mResultField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening);

        mCaptionField = (TextView) findViewById(R.id.caption);
        mResultField = (TextView) findViewById(R.id.result);

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

    private void toast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void setupSpeechController() {
        mCategories = new SpeechCategory[]{
                new LightsSpeechCategory(this, mLightController)
        };

        mCaptionField.setText(R.string.prompt_setup);
        mController = new SpeechController(this, mCategories);
        mController.setSpeechListener(new SpeechController.SpeechListener() {
            @Override
            public void onSpeechReady() {}

            @Override
            public void onSpeechError(Exception e) {
                mCaptionField.setText(e.getMessage());
                toast(e.getMessage());
            }

            @Override
            public void onBeginSpeechCategory(SpeechCategory category) {
                if (category == null) {
                    mCaptionField.setText(R.string.prompt_ready);
                } else {
                    mCaptionField.setText(category.getMessage());
                }
            }

            @Override
            public void onPartialResult(String text) {
                mResultField.setText(text);
            }

            @Override
            public void onSpeechResult(String text) {
                mResultField.setText("");
                if (text != null) {
                    toast(text);
                }
            }
        });
    }
}
