package com.matthewn4444.voiceautomation.lights;

import android.content.Context;

import java.util.List;

import lifx.java.android.client.LFXClient;
import lifx.java.android.entities.LFXHSBKColor;
import lifx.java.android.entities.LFXTypes;
import lifx.java.android.light.LFXLight;
import lifx.java.android.light.LFXTaggedLightCollection;
import lifx.java.android.network_context.LFXNetworkContext;

public class LFXController implements LightsSpeechCategory.ILightController {
    private final Context mCtx;
    private final LFXNetworkContext mLNCtx;

    private boolean mIsConnected;

    public LFXController(Context ctx) {
        mCtx = ctx;
        mLNCtx = LFXClient.getSharedInstance(ctx).getLocalNetworkContext();
        mIsConnected = false;

        mLNCtx.addNetworkContextListener(new LFXNetworkContext.LFXNetworkContextListener() {
            @Override
            public void networkContextDidConnect(LFXNetworkContext networkContext) {
                mIsConnected = true;
            }

            @Override
            public void networkContextDidDisconnect(LFXNetworkContext networkContext) {
                mIsConnected = false;
            }

            @Override
            public void networkContextDidAddTaggedLightCollection(
                    LFXNetworkContext networkContext, LFXTaggedLightCollection collection) {
            }

            @Override
            public void networkContextDidRemoveTaggedLightCollection(
                    LFXNetworkContext networkContext, LFXTaggedLightCollection collection) {
            }
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
