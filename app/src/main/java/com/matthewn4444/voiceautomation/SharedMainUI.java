package com.matthewn4444.voiceautomation;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

public class SharedMainUI {
    private final TextView mCaption;
    private final TextView mSubcaption;
    private final View mBackground;

    public SharedMainUI(TextView caption, TextView subcaption, View backgroundView) {
        mCaption = caption;
        mSubcaption = subcaption;
        mBackground = backgroundView;
    }

    public void setText(int resourceId) {
        setText(resourceId, 0);
    }

    public void setText(String captionText) {
        setText(captionText, null);
    }

    public void setText(String captionText, String subtext) {
        mCaption.setText(captionText);
        mSubcaption.setText(subtext);
    }

    public void setText(int resCaptionId, int resSubId) {
        mCaption.setText(resCaptionId);
        if (resSubId == 0) {
            mSubcaption.setText(null);
        } else {
            mSubcaption.setText(resSubId);
        }
    }

    public void setTextColor(int color) {
        setTextColor(color, color);
    }

    public void setTextColor(int captionColor, int subcaptionColor) {
        mCaption.setTextColor(captionColor);
        mSubcaption.setTextColor(subcaptionColor);
    }

    public void animateTextColor(int fromColor, int toColor) {
        CategoryPresenter.animateTextColor(mCaption, fromColor, toColor);
        CategoryPresenter.animateTextColor(mSubcaption, fromColor, toColor);
    }

    public void setBackground(Drawable drawable) {
        mBackground.setBackground(drawable);
    }

    public void setBackgroundColor(int color) {
        mBackground.setBackgroundColor(color);
    }

    public void clearBackground() {
        mBackground.setBackground(null);
    }

    public View getBackgroundView() {
        return mBackground;
    }
}
