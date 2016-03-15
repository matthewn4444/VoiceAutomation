package com.matthewn4444.voiceautomation;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class UIPresenter implements SpeechController.SpeechListener, SpeechCategory.OnUIStateChangedListener {
    public static final int CATEGORY_IMAGE_TIMEOUT = 5000;

    private final Activity mActivity;

    private final SharedMainUI mSharedUI;
    private final TextView mResultField;
    private final TextView mDateField;
    private final TextView mTimeField;
    private final ViewGroup mMainCategoryHolder;
    private final HashMap<SpeechCategory, View> mCategoryViews;
    private final int mUITextColor;

    private final Animation mMainImageSlideIn;
    private final Animation mMainImageSlideOut;
    private final Animation mResultTextFadeOut;
    private final Animation mMainImageFadeIn;
    private final Animation mMainImageFadeOut;

    private SpeechCategory mPriorityCategory;
    private SpeechCategory mCurrentCategory;
    private Timer mHideTimer;
    private SecondCounter mSecondCounter;
    private boolean mCategoryImageIsShowing;
    private boolean mIsReady;

    public UIPresenter(Activity activity, HashMap<String, SpeechCategory> categories) {
        mActivity = activity;
        mUITextColor = activity.getResources().getColor(R.color.ui_text_color);
        mResultField = (TextView) activity.findViewById(R.id.result);
        mTimeField = (TextView) activity.findViewById(R.id.time);
        mDateField = (TextView) activity.findViewById(R.id.date);
        mMainCategoryHolder = (ViewGroup) activity.findViewById(R.id.main_category_holder);
        mCategoryViews = new HashMap<>();
        mSharedUI = new SharedMainUI((TextView) activity.findViewById(R.id.caption),
                (TextView) activity.findViewById(R.id.subcaption),
                activity.findViewById(R.id.background));
        speechHasReset(categories);

        mMainImageSlideIn = AnimationUtils.loadAnimation(mActivity, R.anim.slide_in_main_image);
        mMainImageSlideOut = AnimationUtils.loadAnimation(mActivity, R.anim.slide_out_main_image);
        mResultTextFadeOut = AnimationUtils.loadAnimation(mActivity, R.anim.result_text_fade_out);
        mMainImageFadeIn = AnimationUtils.loadAnimation(mActivity, R.anim.fade_in_main_image);
        mMainImageFadeOut = AnimationUtils.loadAnimation(mActivity, R.anim.fade_out_main_image);

        setupAnimations();

        setupSecondCounter();
    }

    @Override
    public void onSpeechReady() {}

    @Override
    public void onSpeechError(Exception e) {
        mSharedUI.setText(e.getMessage());
        Toast.makeText(mActivity.getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBeginSpeechCategory(SpeechCategory category) {
        if (category == null) {
            if (mPriorityCategory != null) {
                onUIStateChanged(mPriorityCategory);
            } else {
                mIsReady = true;
                mSharedUI.setText(R.string.prompt_ready);
            }
        } else {
            cancelTimer();
            mSharedUI.setText(category.getMessage());
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
            mResultField.clearAnimation();
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
                        }
                    });
                }
            }, CATEGORY_IMAGE_TIMEOUT);

            updatePriority();
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
            mSharedUI.setText(R.string.prompt_locked, R.string.prompt_locked_message);
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
        mPriorityCategory = category;
        category.handleMainUI(mSharedUI);
    }

    public void onPause() {
        mSecondCounter.stop();
    }

    public void onResume() {
        updateTime();
        mSecondCounter.start();
    }

    public void speechHasReset(HashMap<String, SpeechCategory> categories) {
        mActivity.getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            mSharedUI.setText(R.string.prompt_setup);
        } else {
            mSharedUI.setText(R.string.prompt_no_permissions);
        }
        mIsReady = false;

        for (SpeechCategory category : mCategoryViews.keySet()) {
            View view = mCategoryViews.get(category);
            ViewGroup layout = (ViewGroup)view.getParent();
            layout.removeAllViews();
            category.getPresenter().onDetachView(layout);
            mMainCategoryHolder.removeView(layout);
            category.setOnStateChangedListener(null);
        }
        mCategoryViews.clear();

        // Setup the view categories lookup
        for (String command: categories.keySet()) {
            SpeechCategory category = categories.get(command);
            FrameLayout layout = new FrameLayout(mActivity);
            mMainCategoryHolder.addView(layout);
            mCategoryViews.put(category, category.getPresenter().onAttachView(layout, mSharedUI, category));
            layout.setAlpha(0.0f);
            category.setOnStateChangedListener(this);
        }
        mSharedUI.setTextColor(mUITextColor);

        mPriorityCategory = null;
        updatePriority();
    }

    public void immediatelyHideCategory() {
        mCategoryImageIsShowing = false;

        // Hide all categories
        for (SpeechCategory category : mCategoryViews.keySet()) {
            ((View) mCategoryViews.get(category).getParent()).setAlpha(0f);
        }

        // TODO remove hardcode for color
        mActivity.getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        mSharedUI.setTextColor(mUITextColor);
    }

    private void cancelTimer() {
        if (mHideTimer != null) {
            mHideTimer.cancel();
            mHideTimer = null;
        }
    }

    private void updatePriority() {
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
                mPriorityCategory = cate;
                onUIStateChanged(mPriorityCategory);
            }
        } else {
            if (mPriorityCategory != null) {
                mPriorityCategory = null;
            }
            mSharedUI.clearBackground();
            if (mIsReady) {
                mSharedUI.setText(R.string.prompt_ready);
            }
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

    private void setupSecondCounter() {
        mSecondCounter = new SecondCounter(mActivity);
        mSecondCounter.setOnTimeUpdateListener(new SecondCounter.OnTimeUpdateListener() {
            @Override
            public void onTimeUpdate() {
                updateTime();
            }
        });
        mSecondCounter.start();
    }

    private void showCategory(SpeechCategory category) {
        if (category != null) {
            View view = mCategoryViews.get(category);
            FrameLayout layout = (FrameLayout) view.getParent();

            // Show the main image
            layout.setAlpha(1.0f);
            view.clearAnimation();
            view.startAnimation(mMainImageSlideIn);
            layout.clearAnimation();
            layout.startAnimation(mMainImageFadeIn);

            // Animate the text color back to normal
            CategoryPresenter presenter = category.getPresenter();
            mSharedUI.animateTextColor(mUITextColor, presenter.getTextColor(category));
            presenter.onShowPresenter(layout, category);
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
            mSharedUI.animateTextColor(presenter.getTextColor(category), mUITextColor);
            presenter.onHidePresenter(category);
        }
    }

    private void updateTime() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        String dayOfWeek = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
        String month = now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        String hourStr = hour == 12 || hour == 0 ? "12" : Integer.toString(hour % 12);
        String minStr = minute < 10 ? "0" + minute : Integer.toString(minute);
        mTimeField.setText(hourStr + ":" + minStr);
        mDateField.setText(dayOfWeek + ", " + month + " " + now.get(Calendar.DAY_OF_MONTH));
    }
}
