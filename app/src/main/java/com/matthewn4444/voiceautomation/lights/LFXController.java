package com.matthewn4444.voiceautomation.lights;

import android.content.Context;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import lifx.java.android.client.LFXClient;
import lifx.java.android.entities.LFXHSBKColor;
import lifx.java.android.entities.LFXTypes;
import lifx.java.android.light.LFXLight;
import lifx.java.android.light.LFXLightCollection;
import lifx.java.android.network_context.LFXNetworkContext;

public class LFXController implements LightsSpeechCategory.ILightController {
    public final int LIGHT_CONNECTION_TIMEOUT = 500;

    private final Context mCtx;
    private final LFXNetworkContext mLNCtx;

    private OnConnectionChangedListener mListener;
    private boolean mIsConnected;
    private Timer mStateChangeTimer;
    private final Object mLightLock = new Object();

    public LFXController(Context ctx) {
        mCtx = ctx;
        mLNCtx = LFXClient.getSharedInstance(ctx).getLocalNetworkContext();
        mIsConnected = false;

        mLNCtx.getAllLightsCollection().addLightCollectionListener(new LFXLightCollection.LFXLightCollectionListener() {
            @Override
            public void lightCollectionDidAddLight(final LFXLightCollection lightCollection, LFXLight light) {
                // Checks to make sure all lights have connected before throwing the event
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
                                if (mListener != null) {
                                    mListener.onConnectionChanged(lightCollection.getLights().size(), true);
                                }
                                mStateChangeTimer = null;
                            }
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
                                if (mListener != null) {
                                    mListener.onConnectionChanged(lightCollection.getLights().size(), false);
                                }
                                mStateChangeTimer = null;
                            }
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
        });
    }

    @Override
    public boolean isAvailable() {
        return mIsConnected && mLNCtx.getAllLightsCollection().getLights().size() > 0;
    }

    @Override
    public void setBrightnessPercentage(int percentage) {
        if (mIsConnected) {
            float brightness = Math.max(Math.min(100.0f, (float) percentage / 100.0f), 0.0f);
            LFXHSBKColor color = internalGetColor();
            LFXHSBKColor newColor = LFXHSBKColor.getColor(color.getHue(), color.getSaturation(),
                    brightness, color.getKelvin());
            mLNCtx.getAllLightsCollection().setColor(newColor);
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
        mLNCtx.connect();
    }

    @Override
    public void disconnect() {
        mLNCtx.disconnect();
    }
}
