package com.matthewn4444.voiceautomation.lights;

import android.content.Context;
import android.util.Log;

import com.matthewn4444.lifx.remote.LIFXBulb;
import com.matthewn4444.lifx.remote.LIFXRemote;
import com.matthewn4444.lifx.remote.LIFXRemoteResponse;
import com.matthewn4444.lifx.remote.LIFXResponseException;
import com.matthewn4444.lifx.remote.LIFXState;
import com.matthewn4444.voiceautomation.LazyPref;
import com.matthewn4444.voiceautomation.R;

import java.util.List;

public class LFXController implements LightsSpeechCategory.ILightController,
        LIFXRemote.OnRemoteCommandFinishedListener {
    private static final String TAG = "LFXController";

    private final LIFXRemote mRemote;

    private OnConnectionChangedListener mListener;
    private boolean mIsConnected;
    private float mCurrentBrightness;               // TODO a hack till lan is done
    private boolean mCurrentlyOn;

    public LFXController(Context ctx) {
        String token = LazyPref.getString(ctx, R.string.settings_general_light_lifx_remote_token_key);
        mRemote = new LIFXRemote(token);
        mRemote.setListener(this);
        mIsConnected = false;

        mCurrentBrightness = -1;
        mCurrentlyOn = false;
    }

    @Override
    public boolean isAvailable() {
        return mIsConnected;
    }

    @Override
    public void setBrightnessPercentage(int percentage) {
        setBrightnessPercentage(percentage, 1000);
    }

    @Override
    public void setBrightnessPercentage(int percentage, int duration) {
        mCurrentBrightness = Math.max(Math.min(100.0f, (float) percentage / 100.0f), 0.0f);
        mRemote.setAllState(LIFXState.PowerOn, null, mCurrentBrightness, duration);
    }

    @Override
    public int getBrightnessPercentage() {
        if (mIsConnected && mCurrentBrightness != -1) {
            return Math.round(mCurrentBrightness * 100);
        }
        return 0;
    }

    @Override
    public void turnOff() {
        mCurrentlyOn = false;
        mRemote.turnAllOff();
    }

    @Override
    public void turnOn() {
        mCurrentlyOn = true;
        mRemote.turnAllOn();
    }

    @Override
    public boolean isOn() {
        return mIsConnected && mCurrentlyOn;
    }

    @Override
    public void setOnConnectionChangedListener(OnConnectionChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void connect() {
        if (!mRemote.isRunning()) {
            mRemote.start();
            mRemote.listAllLights();
        } else if (!mIsConnected) {
            mRemote.listAllLights();
        }
    }

    @Override
    public void disconnect() {
        if (mRemote.isRunning()) {
            mRemote.destroy();
        }
    }

    @Override
    public void onRemoteCommandFinished(int command, LIFXRemoteResponse response) {
        if (response.warnings != null) {
            for (LIFXRemoteResponse.Warning warning: response.warnings) {
                Log.w(TAG, warning.message);
            }
        }

        List<LIFXBulb> bulbs = mRemote.getAllBulbs();
        if (!bulbs.isEmpty()) {
            mCurrentlyOn = bulbs.get(0).isOn();
            mCurrentBrightness = bulbs.get(0).brightness();
        }

        if (mIsConnected && mListener != null) {
            mListener.onCommandFinished();
        }

        // Check to see if we just connected
        if (!mIsConnected && !bulbs.isEmpty()) {
            mIsConnected = true;
            if (mListener != null) {
                mListener.onConnectionChanged(bulbs.size(), true);
            }
        } else if (mIsConnected && bulbs.isEmpty()) {
            mIsConnected = false;
            if (mListener != null) {
                mListener.onConnectionChanged(0, false);
            }
        }
    }

    @Override
    public void onLIFXError(LIFXResponseException e) {
        e.printStackTrace();
        if (mListener != null) {
            mListener.onError(e);
        }
    }
}
