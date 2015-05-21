package com.matthewn4444.voiceautomation;

import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.widget.Toast;

import com.matthewn4444.voiceautomation.lights.LFXController;
import com.matthewn4444.voiceautomation.lights.LightsAutomator;
import com.matthewn4444.voiceautomation.lights.LightsSpeechCategory;


public class ListeningActivity extends AppCompatActivity implements
        LocationHelper.OnLocationFoundListener {
    public static final int MAX_LIGHT_BRIGHTNESS = 70;
    public static final int REQUEST_CODE_LOCATION_SETTINGS = 1;

    private SpeechCategory[] mCategories;
    private SpeechController mController;
    private LightsAutomator mLightAutomator;
    private LocationHelper mLocationHelper;
    private LightsSpeechCategory.ILightController mLightController;

    private boolean mLocationIsReady = false;
    private boolean mLightsIsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mLightController = new LFXController(this);
        mLightController.setOnConnectionChangedListener(new LightsSpeechCategory.ILightController.OnConnectionChangedListener() {
            @Override
            public void onConnectionChanged(int lightsConnected, boolean justConnected) {
                if (!mLightsIsReady && justConnected) {
                    // Initial connection with the lights have been made, now we can automate them
                    mLightsIsReady = true;
                    startLightAutomation();
                }
            }
        });

        setupSpeechController();

        mLocationHelper = new LocationHelper(this);
        mLocationHelper.queryLocation(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLocationHelper.onPause();
        mController.pause();
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

    private void setupSpeechController() {
        UIPresenter presenter = new UIPresenter(this);
        mCategories = new SpeechCategory[]{
                new LightsSpeechCategory(this, presenter, mLightController)
        };
        mController = new SpeechController(this, mCategories);
        mController.setSpeechListener(presenter);
    }

    private void startLightAutomation() {
        if (mLightsIsReady && mLocationIsReady) {
            mLightAutomator = new LightsAutomator(ListeningActivity.this,
                    mLocationHelper.getLastLocation(), mLightController, MAX_LIGHT_BRIGHTNESS);
        }
    }

    @Override
    public void onLocationFound(Location location) {
        if (location != null) {
            mLocationIsReady = true;
            startLightAutomation();
        } else {
            Toast.makeText(this, R.string.location_is_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}
