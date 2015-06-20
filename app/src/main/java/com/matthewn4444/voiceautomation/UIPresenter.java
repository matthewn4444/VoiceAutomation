package com.matthewn4444.voiceautomation;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class UIPresenter implements SpeechController.SpeechListener, SpeechCategory.OnUIStateChangedListener {
    public static final int CATEGORY_IMAGE_TIMEOUT = 5000;

    private final Activity mActivity;

    private final TextView mCaptionField;
    private final TextView mResultField;
    private final ViewGroup mMainCategoryHolder;
    private final HashMap<SpeechCategory, View> mCategoryViews;
    private final View mBackgroundView;

    private final Animation mMainImageSlideIn;
    private final Animation mMainImageSlideOut;
    private final Animation mResultTextFadeOut;
    private final Animation mMainImageFadeIn;
    private final Animation mMainImageFadeOut;

    private SpeechCategory mPriorityCategory;
    private SpeechCategory mCurrentCategory;
    private Timer mHideTimer;
    private boolean mCategoryImageIsShowing;

    public UIPresenter(Activity activity) {
        mActivity = activity;
        mCaptionField = (TextView) activity.findViewById(R.id.caption);
        mResultField = (TextView) activity.findViewById(R.id.result);
        mMainCategoryHolder = (ViewGroup) activity.findViewById(R.id.main_category_holder);
        mBackgroundView = activity.findViewById(R.id.background);
        mCategoryViews = new HashMap<>();
        speechHasReset();

        mMainImageSlideIn = AnimationUtils.loadAnimation(mActivity, R.anim.slide_in_main_image);
        mMainImageSlideOut = AnimationUtils.loadAnimation(mActivity, R.anim.slide_out_main_image);
        mResultTextFadeOut = AnimationUtils.loadAnimation(mActivity, R.anim.result_text_fade_out);
        mMainImageFadeIn = AnimationUtils.loadAnimation(mActivity, R.anim.fade_in_main_image);
        mMainImageFadeOut = AnimationUtils.loadAnimation(mActivity, R.anim.fade_out_main_image);

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
            mCaptionField.setText(category.getMessage());
            if (!mCategoryImageIsShowing) {
                mCategoryImageIsShowing = true;
                showCategory(category);
            } else if (mCurrentCategory != null && mCurrentCategory != category) {
                // Swap to the new command
                hideCategory(mCurrentCategory);
                showCategory(category);
            }
            mCurrentCategory = category;
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
                            hideCategory(mCurrentCategory);
                            mCurrentCategory = null;

                            // Recalculate the new priority category to handle main UI
                            SpeechCategory cate = null;
                            int currentPriority = SpeechCategory.NO_PRIORITY;
                            for (SpeechCategory category : mCategoryViews.keySet()) {
                                int priority = category.getUIPriority();
                                if (priority > currentPriority) {
                                    currentPriority = priority;
                                    cate = category;
                                }
                            }
                            if (cate != null) {
                                if (mPriorityCategory != cate) {
                                    if (mPriorityCategory != null) {
                                        mPriorityCategory.setOnStateChangedListener(null);
                                    }
                                    mPriorityCategory = cate;
                                    mPriorityCategory.setOnStateChangedListener(UIPresenter.this);
                                    onUIStateChanged(mPriorityCategory);
                                }
                            } else {
                                if (mPriorityCategory != null) {
                                    mPriorityCategory.setOnStateChangedListener(null);
                                    mPriorityCategory = null;
                                }
                                mBackgroundView.setBackground(null);
                            }
                        }
                    });
                }
            }, CATEGORY_IMAGE_TIMEOUT);
        } else {
            mResultField.setText("");
            mResultField.setVisibility(View.GONE);
            if (text == null && mCurrentCategory != null) {
                mCategoryImageIsShowing = false;
                hideCategory(mCurrentCategory);
                mCurrentCategory = null;
            }
        }
    }

    @Override
    public void onLock(boolean isLocked) {
        cancelTimer();
        if (isLocked) {
            mCaptionField.setText(R.string.prompt_locked);
            if (mCategoryImageIsShowing) {
                hideCategory(mCurrentCategory);
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
    public void onUIStateChanged(SpeechCategory category) {
        category.handleMainUI(mBackgroundView, mCaptionField);
    }

    public void speechHasReset() {
        mCaptionField.setText(R.string.prompt_setup);

        for (SpeechCategory category : mCategoryViews.keySet()) {
            View view = mCategoryViews.get(category);
            ViewGroup layout = (ViewGroup)view.getParent();
            layout.removeAllViews();
            category.getPresenter().onDetachView(layout);
            mMainCategoryHolder.removeView(layout);
        }
        mCategoryViews.clear();
    }

    public void immediatelyHideCategory() {
        mCategoryImageIsShowing = false;

        // Hide all categories
        for (SpeechCategory category : mCategoryViews.keySet()) {
            ((View) mCategoryViews.get(category).getParent()).setAlpha(0f);
        }

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

        mMainImageFadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                for (int i = 0; i < mMainCategoryHolder.getChildCount(); i++) {
                    mMainCategoryHolder.getChildAt(i).setAlpha(0f);
                }
                if (mCurrentCategory != null) {
                    ((View)mCategoryViews.get(mCurrentCategory).getParent()).setAlpha(1.0f);
                }
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }

    private void showCategory(SpeechCategory category) {
        if (category != null) {
            View view = mCategoryViews.get(category);
            FrameLayout layout;
            if (view == null) {
                layout = new FrameLayout(mActivity);
                view = category.getPresenter().onAttachView(layout, mCaptionField, category);
                mMainCategoryHolder.addView(layout);
                mCategoryViews.put(category, view);
            } else {
                layout = (FrameLayout) view.getParent();
            }

            // Show the main image
            layout.setAlpha(1.0f);
            view.clearAnimation();
            view.startAnimation(mMainImageSlideIn);
            layout.clearAnimation();
            layout.startAnimation(mMainImageFadeIn);

            // Animate the text color back to normal
            CategoryPresenter presenter = category.getPresenter();
            CategoryPresenter.animateTextColor(mCaptionField, Color.WHITE, presenter.getTextColor(category));
            presenter.onShowPresenter(category);
        }
    }

    private void hideCategory(SpeechCategory category) {
        if (category != null) {
            View view = mCategoryViews.get(category);
            FrameLayout layout = (FrameLayout) view.getParent();

            // Hide the main image
            view.clearAnimation();
            view.startAnimation(mMainImageSlideOut);
            layout.clearAnimation();
            layout.startAnimation(mMainImageFadeOut);

            // Animate the text color back to normal
            CategoryPresenter presenter = category.getPresenter();
            CategoryPresenter.animateTextColor(mCaptionField, presenter.getTextColor(category), Color.WHITE);
            presenter.onHidePresenter(category);
        }
    }
}
