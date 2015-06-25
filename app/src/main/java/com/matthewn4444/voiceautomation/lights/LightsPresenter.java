package com.matthewn4444.voiceautomation.lights;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.matthewn4444.voiceautomation.CategoryPresenter;
import com.matthewn4444.voiceautomation.R;
import com.matthewn4444.voiceautomation.SharedMainUI;
import com.matthewn4444.voiceautomation.SpeechCategory;

public class LightsPresenter extends CategoryPresenter {
    public static final int DefaultColorOn = Color.YELLOW;
    public static final int DefaultColorOff = Color.GRAY;

    private ImageView mBackImage;
    private ImageView mFrontImage;
    private FrameLayout mImageContainer;

    public LightsPresenter(Context ctx) {
        super(DefaultColorOn, ctx.getResources().getColor(R.color.lights_main_text_color));
    }

    @Override
    public View onAttachView(ViewGroup parent, SharedMainUI ui, SpeechCategory category) {
        Context context = parent.getContext();
        synchronized (this) {
            if (mImageContainer == null) {
                mImageContainer = new FrameLayout(context);
                mBackImage = new ImageView(context);
                mFrontImage = new ImageView(context);
                mImageContainer.addView(mBackImage);
                mImageContainer.addView(mFrontImage);

                mBackImage.setImageResource(R.drawable.light_bulb_off);
                mFrontImage.setImageResource(R.drawable.light_bulb_on);
                parent.addView(mImageContainer);
            }
        }
        onUpdatePresenter(parent, category);
        return mImageContainer;
    }

    @Override
    public void onDetachView(ViewGroup parent) {
        synchronized (this) {
            if (mImageContainer != null) {
                mImageContainer.removeAllViews();
                mImageContainer = null;
                mFrontImage = null;
                mBackImage = null;
            }
        }
    }

    @Override
    public void onUpdatePresenter(ViewGroup parent, SpeechCategory category) {
        super.onUpdatePresenter(parent, category);
        LightsSpeechCategory cate = (LightsSpeechCategory) category;
        int brightness = cate.getBrightness();
        boolean isOn = cate.isOn();
        updateColor(parent, brightness, isOn, false);
    }

    @Override
    public void onShowPresenter(ViewGroup parent, SpeechCategory category) {
        super.onShowPresenter(parent, category);
        onUpdatePresenter(parent, category);
    }

    public void animateChange(int brightnessPercentage, boolean isOn) {
        updateColor((ViewGroup) mImageContainer.getParent(), brightnessPercentage, isOn, true);
    }

    private void updateColor(ViewGroup parent, int brightness, boolean isOn, boolean animate) {
        float imageOpacity = isOn ? brightness * 1.0f / 100.0f : 0;
        if (animate) {
            float alpha = mFrontImage.getAlpha();
            animateOpacity(mFrontImage, alpha, imageOpacity);
            animateBackgroundColor(parent,
                    blendColors(DefaultColorOn, DefaultColorOff, alpha),
                    blendColors(DefaultColorOn, DefaultColorOff, imageOpacity));
        } else {
            if (isOn) {
                mFrontImage.setAlpha(imageOpacity);
                parent.setBackgroundColor(blendColors(DefaultColorOn, DefaultColorOff, imageOpacity));
            } else {
                mFrontImage.setAlpha(0f);
                parent.setBackgroundColor(DefaultColorOff);
            }
        }
    }
}
