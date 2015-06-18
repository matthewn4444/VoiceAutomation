package com.matthewn4444.voiceautomation.music;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.matthewn4444.voiceautomation.CategoryPresenter;
import com.matthewn4444.voiceautomation.R;
import com.matthewn4444.voiceautomation.SpeechCategory;

public class MusicPresenter extends CategoryPresenter {
    private static final int DELAY_ANIMATION_STEP = 150;

    private ImageView mMainImage;
    private FloatingActionButton mShuffleButton;
    private FloatingActionButton mRepeatButton;

    private Animation mSlideInAnimation1;
    private Animation mSlideInAnimation2;
    private Animation mSlideOutAnimation;

    private int mColorOn;
    private int mColorOff;

    private boolean mShuffleState;
    private boolean mRepeatState;

    public MusicPresenter(Context context) {
        super(context.getResources().getColor(R.color.music_main_background), Color.WHITE);
    }

    @Override
    public View onAttachView(ViewGroup parent, TextView caption, SpeechCategory category) {
        synchronized (this) {
            if (mMainImage == null) {
                final Context ctx = parent.getContext();
                mMainImage = new ImageView(ctx);
                mMainImage.setImageResource(R.drawable.music);
                parent.addView(mMainImage);

                LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View v = inflater.inflate(R.layout.music_controls_layout, parent, true);

                mShuffleButton = (FloatingActionButton) v.findViewById(R.id.shuffle);
                mRepeatButton = (FloatingActionButton) v.findViewById(R.id.repeat);

                // Setup the animations
                mSlideInAnimation1 = AnimationUtils.loadAnimation(ctx, R.anim.slide_in_controls);
                mSlideInAnimation2 = AnimationUtils.loadAnimation(ctx, R.anim.slide_in_controls);
                mSlideOutAnimation = AnimationUtils.loadAnimation(ctx, R.anim.slide_out_main_image);
                mSlideInAnimation1.setStartOffset(DELAY_ANIMATION_STEP);
                mSlideInAnimation2.setStartOffset(DELAY_ANIMATION_STEP * 2);
                mSlideOutAnimation.setStartOffset(DELAY_ANIMATION_STEP);

                mShuffleState = false;
                mRepeatState = false;

                mColorOn = ctx.getResources().getColor(R.color.music_controls_background_on);
                mColorOff = ctx.getResources().getColor(R.color.music_controls_background_off);
            }
        }
        onUpdatePresenter(parent, category);
        return mMainImage;
    }

    @Override
    public void onDetachView(ViewGroup parent) {
        synchronized (this) {
            mMainImage = null;
        }
    }

    @Override
    public void onShowPresenter(SpeechCategory category) {
        super.onShowPresenter(category);

        updateState(category);
        mShuffleButton.startAnimation(mSlideInAnimation1);
        mRepeatButton.startAnimation(mSlideInAnimation2);
    }

    @Override
    public void onHidePresenter(SpeechCategory category) {
        super.onHidePresenter(category);

        mShuffleButton.startAnimation(mSlideOutAnimation);
        mRepeatButton.startAnimation(mSlideOutAnimation);
    }

    @Override
    public void onUpdatePresenter(ViewGroup parent, SpeechCategory category) {
        super.onUpdatePresenter(parent, category);
        updateState(category);
    }

    public void updateState(SpeechCategory category) {
        MusicSpeechCategory cate = (MusicSpeechCategory) category;
        boolean shuffleOn = cate.isShuffleOn();
        boolean repeatOn = cate.isRepeatOn();
        if (mShuffleState != shuffleOn) {
            if (shuffleOn) {
                animateBackgroundColor(mShuffleButton, mColorOff, mColorOn);
            } else {
                animateBackgroundColor(mShuffleButton, mColorOn, mColorOff);
            }
            mShuffleState = shuffleOn;
        }
        if (mRepeatState != repeatOn) {
            if (repeatOn) {
                animateBackgroundColor(mRepeatButton, mColorOff, mColorOn);
            } else {
                animateBackgroundColor(mRepeatButton, mColorOn, mColorOff);
            }
            mRepeatState = repeatOn;
        }
    }

    private void animateBackgroundColor(final FloatingActionButton button, int from, int to) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(COLOR_ANIMATION_DURATION);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                button.setBackgroundTintList(ColorStateList.valueOf((int) animation.getAnimatedValue()));
            }
        });
        animator.start();
    }
}
