package com.matthewn4444.voiceautomation;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.matthewn4444.voiceautomation.lights.LFXController;
import com.matthewn4444.voiceautomation.lights.LightsSpeechCategory;

import java.util.Timer;
import java.util.TimerTask;


public class ListeningActivity extends ActionBarActivity {
    private static final int CATEGORY_IMAGE_TIMEOUT = 5000;

    private SpeechCategory[] mCategories;
    private SpeechController mController;
    private LightsSpeechCategory.ILightController mLightController;

    private Animation mMainImageSlideIn;
    private Animation mMainImageSlideOut;
    private Animation mResultTextFadeOut;

    private TextView mCaptionField;
    private TextView mResultField;
    private ImageView mMainImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listening);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mCaptionField = (TextView) findViewById(R.id.caption);
        mResultField = (TextView) findViewById(R.id.result);
        mMainImage = (ImageView) findViewById(R.id.main_image);

        mLightController = new LFXController(this);

        setupSpeechController();
        setupAnimations();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mController.pause();
        for (SpeechCategory cate: mCategories) {
            cate.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    private void toast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void setupSpeechController() {
        mCategories = new SpeechCategory[]{
                new LightsSpeechCategory(this, mLightController)
        };

        mCaptionField.setText(R.string.prompt_setup);
        mController = new SpeechController(this, mCategories);
        mController.setSpeechListener(new SpeechController.SpeechListener() {
            private SpeechCategory mCurrentCategory;
            private Timer mHideTimer;
            private boolean mCategoryImageIsShowing;

            @Override
            public void onSpeechReady() {}

            @Override
            public void onSpeechError(Exception e) {
                mCaptionField.setText(e.getMessage());
                toast(e.getMessage());
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
                if (text != null && !mResultField.getText().equals("")) {
                    mResultField.setText(text);
                    mResultField.clearAnimation();
                    mResultField.startAnimation(mResultTextFadeOut);
                    cancelTimer();
                    mHideTimer = new Timer();
                    mHideTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
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
                Toast.makeText(ListeningActivity.this, "'" + category.getActivationCommand()
                        + "' is unavailable", Toast.LENGTH_SHORT).show();
            }

            private void cancelTimer() {
                if (mHideTimer != null) {
                    mHideTimer.cancel();
                    mHideTimer = null;
                }
            }
        });
    }

    private void setupAnimations() {
        mMainImageSlideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_main_image);
        mMainImageSlideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_main_image);
        mResultTextFadeOut = AnimationUtils.loadAnimation(this, R.anim.result_text_fade_out);

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

    private void animateBackgroundColor(int from, int to, long duration) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(duration);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                getWindow().getDecorView().setBackgroundColor((int)animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    private void animateCaptionColor(int from, int to, long duration) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.setDuration(duration);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCaptionField.setTextColor((int) animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    private void animateCategoryImage(SpeechCategory category, boolean show) {
        if (category.getMainResDrawable() != 0) {
            mMainImage.setImageResource(category.getMainResDrawable());
        } else {
            mMainImage.setImageDrawable(category.getMainDrawable());
        }
        mMainImage.setVisibility(View.VISIBLE);
        mMainImage.clearAnimation();
        mMainImage.startAnimation(show ? mMainImageSlideIn : mMainImageSlideOut);
        int currentColor = getResources().getColor(android.R.color.background_dark);
        int currentTextColor = Color.WHITE;
        if (show) {
            animateBackgroundColor(currentColor, category.getMainColor(), 400);
            animateCaptionColor(currentTextColor, category.getMainTextColor(), 400);
        } else {
            animateBackgroundColor(category.getMainColor(), currentColor, 400);
            animateCaptionColor(category.getMainTextColor(), currentTextColor, 400);
        }
    }
}
