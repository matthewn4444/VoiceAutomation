package com.matthewn4444.voiceautomation;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

public class UIPresenter implements SpeechController.SpeechListener, SpeechCategory.ICategoryPresenter {
    public static final int CATEGORY_IMAGE_TIMEOUT = 5000;
    public static final int COLOR_ANIMATION_DURATION = 400;

    private final Activity mActivity;

    private final TextView mCaptionField;
    private final TextView mResultField;
    private final ImageView mMainImage;
    private final ImageView mMainBackImage;
    private final ViewGroup mMainImageHolder;

    private final Animation mMainImageSlideIn;
    private final Animation mMainImageSlideOut;
    private final Animation mResultTextFadeOut;

    private SpeechCategory mCurrentCategory;
    private Timer mHideTimer;
    private boolean mCategoryImageIsShowing;
    private int mCurrentBackgroundColor = Color.BLACK;
    private int mCurrentCaptionColor = Color.WHITE;
    private float mCurrentImageOpacity = 1.0f;

    public UIPresenter(Activity activity) {
        mActivity = activity;
        mCaptionField = (TextView) activity.findViewById(R.id.caption);
        mResultField = (TextView) activity.findViewById(R.id.result);
        mMainImageHolder = (ViewGroup) activity.findViewById(R.id.main_image_bolder);
        mMainImage = (ImageView) activity.findViewById(R.id.main_image_top);
        mMainBackImage = (ImageView) activity.findViewById(R.id.main_image_bottom);
        speechHasReset();

        mMainImageSlideIn = AnimationUtils.loadAnimation(mActivity, R.anim.slide_in_main_image);
        mMainImageSlideOut = AnimationUtils.loadAnimation(mActivity, R.anim.slide_out_main_image);
        mResultTextFadeOut = AnimationUtils.loadAnimation(mActivity, R.anim.result_text_fade_out);

        setupAnimations();
    }

    @Override
    public void onSpeechReady() {}

    @Override
    public void onSpeechError(Exception e) {
        mCaptionField.setText(e.getMessage());
        Toast.makeText(mActivity.getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBeginSpeechCategory(SpeechCategory category) {
        if (category == null) {
            mCaptionField.setText(R.string.prompt_ready);
        } else {
            cancelTimer();
            mCurrentCategory = category;
            mCaptionField.setText(category.getMessage());
            if (!mCategoryImageIsShowing) {
                mCategoryImageIsShowing = true;
                animateCategoryImage(category, true);
            }
        }
    }

    @Override
    public void onPartialResult(String text) {
        if (!text.equals("")) {
            mResultField.setText(text);
            mResultField.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSpeechResult(String text) {
        cancelTimer();
        if (text != null && !mResultField.getText().equals("")) {
            mResultField.setText(text);
            mResultField.clearAnimation();
            mResultField.startAnimation(mResultTextFadeOut);
            mHideTimer = new Timer();
            mHideTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cancelTimer();
                            mCategoryImageIsShowing = false;
                            animateCategoryImage(mCurrentCategory, false);
                        }
                    });
                }
            }, CATEGORY_IMAGE_TIMEOUT);
        } else {
            mResultField.setText("");
            mResultField.setVisibility(View.GONE);
            if (text == null && mCurrentCategory != null) {
                mCategoryImageIsShowing = false;
                animateCategoryImage(mCurrentCategory, false);
            }
            mCurrentCategory = null;
        }
    }

    @Override
    public void onLock(boolean isLocked) {
        cancelTimer();
        if (isLocked) {
            mCaptionField.setText(R.string.prompt_locked);
            if (mCategoryImageIsShowing) {
                animateCategoryImage(mCurrentCategory, false);
                mCategoryImageIsShowing = false;
            }
        }
    }

    @Override
    public void onCategoryUnavailable(SpeechCategory category) {
        Toast.makeText(mActivity, "'" + category.getActivationCommand()
                + "' is unavailable", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void animateBackgroundColor(int to) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), mCurrentBackgroundColor, to);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(COLOR_ANIMATION_DURATION);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mActivity.getWindow().getDecorView().setBackgroundColor(
                        (int)animation.getAnimatedValue());
            }
        });
        animator.start();
        mCurrentBackgroundColor = to;
    }

    @Override
    public void animateCaptionColor(int to) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), mCurrentCaptionColor, to);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(COLOR_ANIMATION_DURATION);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCaptionField.setTextColor((int) animation.getAnimatedValue());
            }
        });
        animator.start();
        mCurrentCaptionColor = to;
    }

    @Override
    public void animateMainImageOpacity(float to) {
        ValueAnimator animator = ObjectAnimator.ofFloat(mCurrentImageOpacity, to);
        animator.setDuration(COLOR_ANIMATION_DURATION);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mMainImage.setAlpha((float)animation.getAnimatedValue());
            }
        });
        animator.start();
        mCurrentImageOpacity = to;
    }

    public void speechHasReset() {
        mCaptionField.setText(R.string.prompt_setup);
    }

    public void immediatelyHideCategory() {
        mCurrentBackgroundColor = Color.BLACK;
        mCurrentCaptionColor = Color.WHITE;
        mCurrentImageOpacity = 0.0f;

        mCategoryImageIsShowing = false;
        mMainBackImage.setVisibility(View.GONE);
        mMainImageHolder.setVisibility(View.GONE);

        // TODO remove hardcode for color
        mActivity.getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        mCaptionField.setTextColor(Color.WHITE);
    }

    private void cancelTimer() {
        if (mHideTimer != null) {
            mHideTimer.cancel();
            mHideTimer = null;
        }
    }

    private void setupAnimations() {
        mResultTextFadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                mResultField.setText("");
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }

    private void animateCategoryImage(SpeechCategory category, boolean show) {
        if (category == null) {
            return;
        }
        mCurrentImageOpacity = category.getMainImageOpacity();
        mMainImage.setImageResource(category.getMainResDrawable());
        mMainImage.setAlpha(mCurrentImageOpacity);
        if (category.getBackResDrawable() != 0) {
            mMainBackImage.setImageResource(category.getBackResDrawable());
            mMainBackImage.setVisibility(View.VISIBLE);
        } else {
            mMainBackImage.setVisibility(View.GONE);
        }
        mMainImageHolder.setVisibility(View.VISIBLE);
        mMainImageHolder.clearAnimation();
        mMainImageHolder.startAnimation(show ? mMainImageSlideIn : mMainImageSlideOut);
        int currentColor = Color.BLACK;
        int currentTextColor = Color.WHITE;
        if (show) {
            animateBackgroundColor(category.getMainColor());
            animateCaptionColor(category.getMainTextColor());
        } else {
            animateBackgroundColor(currentColor);
            animateCaptionColor(currentTextColor);
        }
    }
}
