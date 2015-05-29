package com.matthewn4444.voiceautomation.lights;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import lifx.java.android.client.LFXClient;
import lifx.java.android.entities.LFXHSBKColor;
import lifx.java.android.entities.LFXTypes;
import lifx.java.android.light.LFXLight;
import lifx.java.android.light.LFXLightCollection;
import lifx.java.android.network_context.LFXNetworkContext;

public class LFXController implements LightsSpeechCategory.ILightController,
        LFXLightCollection.LFXLightCollectionListener {
    public final int LIGHT_CONNECTION_TIMEOUT = 500;

    private final LFXNetworkContext mLNCtx;

    private OnConnectionChangedListener mListener;
    private boolean mIsConnected;
    private Timer mStateChangeTimer;
    private final Object mLightLock = new Object();

    public LFXController(Context ctx) {
        mLNCtx = LFXClient.getSharedInstance(ctx).getLocalNetworkContext();
        mIsConnected = internalGetColor() != null && mLNCtx.getAllLightsCollection().getLights().size() > 0;
    }

    @Override
    public boolean isAvailable() {
        return mIsConnected && internalGetColor() != null && mLNCtx.getAllLightsCollection().getLights().size() > 0;
    }

    @Override
    public void setBrightnessPercentage(int percentage) {
        setBrightnessPercentage(percentage, 0);
    }

    @Override
    public void setBrightnessPercentage(int percentage, int duration) {
        if (mIsConnected) {
            float brightness = Math.max(Math.min(100.0f, (float) percentage / 100.0f), 0.0f);
            LFXHSBKColor color = internalGetColor();
            LFXHSBKColor newColor = LFXHSBKColor.getColor(color.getHue(), color.getSaturation(),
                    brightness, color.getKelvin());
            if (duration == 0) {
                mLNCtx.getAllLightsCollection().setColor(newColor);
            } else {
                mLNCtx.getAllLightsCollection().setColorOverDuration(newColor, duration);
            }
        }
    }

    @Override
    public int getBrightnessPercentage() {
        if (mIsConnected) {
            float brightness = internalGetColor().getBrightness();
            return Math.round(brightness * 100);
        }
        return 0;
    }

    @Override
    public void turnOff() {
        if (mIsConnected) {
            mLNCtx.getAllLightsCollection().setPowerState(LFXTypes.LFXPowerState.OFF);
        }
    }

    @Override
    public void turnOn() {
        if (mIsConnected) {
            mLNCtx.getAllLightsCollection().setPowerState(LFXTypes.LFXPowerState.ON);
        }
    }

    @Override
    public boolean isOn() {
        return mIsConnected && mLNCtx.getAllLightsCollection().getFuzzyPowerState() ==
                LFXTypes.LFXFuzzyPowerState.ON;
    }

    @Override
    public void setOnConnectionChangedListener(OnConnectionChangedListener listener) {
        mListener = listener;
    }

    private LFXHSBKColor internalGetColor() {
        List<LFXLight> lights = mLNCtx.getAllLightsCollection().getLights();
        if (lights.isEmpty())
            return null;
        return lights.get(0).getColor();
    }

    @Override
    public void connect() {
        mLNCtx.getAllLightsCollection().removeLightCollectionListener(this);
        mLNCtx.getAllLightsCollection().addLightCollectionListener(this);
        mLNCtx.connect();
    }

    @Override
    public void disconnect() {
        mLNCtx.getAllLightsCollection().removeLightCollectionListener(this);
        mLNCtx.disconnect();
    }

    @Override
    public void lightCollectionDidAddLight(final LFXLightCollection lightCollection, LFXLight light) {
        // Checks to make sure all lights have connected before throwing the event
        Log.i("lunch", "Light connected " + lightCollection.getLights().size());
        synchronized (mLightLock) {
            if (mStateChangeTimer != null) {
                mStateChangeTimer.cancel();
            }
            mStateChangeTimer = new Timer();
            mStateChangeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (mLightLock) {
                        mIsConnected = true;
                        mStateChangeTimer.cancel();
                        mStateChangeTimer = null;
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) {
                                mListener.onConnectionChanged(lightCollection.getLights().size(), true);
                            }
                        }
                    });
                }
            }, LIGHT_CONNECTION_TIMEOUT);
        }
    }

    @Override
    public void lightCollectionDidRemoveLight(final LFXLightCollection lightCollection, LFXLight light) {
        // Checks to make sure all lights have disconnected before throwing the event
        synchronized (mLightLock) {
            if (mStateChangeTimer != null) {
                mStateChangeTimer.cancel();
            }
            mStateChangeTimer = new Timer();
            mStateChangeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (mLightLock) {
                        mIsConnected = false;
                        mStateChangeTimer.cancel();
                        mStateChangeTimer = null;
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) {
                                mListener.onConnectionChanged(lightCollection.getLights().size(), false);
                            }
                        }
                    });
                }
            }, LIGHT_CONNECTION_TIMEOUT);
        }
    }
    @Override
    public void lightCollectionDidChangeLabel(LFXLightCollection lightCollection, String label) {}
    @Override
    public void lightCollectionDidChangeColor(LFXLightCollection lightCollection, LFXHSBKColor color) {}
    @Override
    public void lightCollectionDidChangeFuzzyPowerState(LFXLightCollection lightCollection, LFXTypes.LFXFuzzyPowerState fuzzyPowerState) {}
}
