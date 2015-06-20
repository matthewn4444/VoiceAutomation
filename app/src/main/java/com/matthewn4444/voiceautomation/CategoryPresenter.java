package com.matthewn4444.voiceautomation;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.TextView;

public abstract class CategoryPresenter {
    public static final int COLOR_ANIMATION_DURATION = 400;

    protected final int mMainBackgoundColor;
    protected final int mMainTextColor;

    public CategoryPresenter(int mainBackgoundColor, int mainTextColor) {
        mMainBackgoundColor = mainBackgoundColor;
        mMainTextColor = mainTextColor;
    }

    public static void animateTextColor(final TextView textView, int from, int to) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(COLOR_ANIMATION_DURATION);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                textView.setTextColor((int) animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    /**
     * Here is where the images and other views are instantiated. Attach them to the parent and
     * return the main view that contains the image (or can be an ImageView). The object returned
     * will be animated while other objects that you may add are animated by your presenter. You
     * can also add a background to the parent, that will not move. The entire parent is faded in
     * upon speech command selection.
     * @param parent
     * @param ui
     * @param category
     * @return
     */
    public abstract View onAttachView(ViewGroup parent, SharedMainUI ui, SpeechCategory category);

    /**
     * Detach and null your views that were attached to parent in onAttachView. The view that you
     * returned before is automatically removed for you.
     * @param parent
     */
    public abstract void onDetachView(ViewGroup parent);

    public int getTextColor(SpeechCategory category) {
        return mMainTextColor;
    }

    public void onShowPresenter(SpeechCategory category) {}

    public void onHidePresenter(SpeechCategory category) {}

    public void onUpdatePresenter(ViewGroup parent, SpeechCategory category) {
        parent.setBackgroundColor(mMainBackgoundColor);
    }

    protected void animateOpacity(final View view, float from, float to) {
        ValueAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, from, to);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(COLOR_ANIMATION_DURATION);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                view.setAlpha((float) animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    protected void animateBackgroundColor(final View view, int from, int to) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(COLOR_ANIMATION_DURATION);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                view.setBackgroundColor((int) animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    protected int blendColors(int color1, int color2, float ratio) {
        final float inverseRation = 1f - ratio;
        float r = (Color.red(color1) * ratio) + (Color.red(color2) * inverseRation);
        float g = (Color.green(color1) * ratio) + (Color.green(color2) * inverseRation);
        float b = (Color.blue(color1) * ratio) + (Color.blue(color2) * inverseRation);
        return Color.rgb((int) r, (int) g, (int) b);
    }
}
