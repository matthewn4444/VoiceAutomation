package com.matthewn4444.voiceautomation.music;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.support.design.widget.FloatingActionButton;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.matthewn4444.voiceautomation.CategoryPresenter;
import com.matthewn4444.voiceautomation.LazyPref;
import com.matthewn4444.voiceautomation.R;
import com.matthewn4444.voiceautomation.SharedMainUI;
import com.matthewn4444.voiceautomation.SpeechCategory;

import java.util.UUID;

public class MusicPresenter extends CategoryPresenter {
    private static final int DELAY_ANIMATION_STEP = 150;

    private final RenderScript mRS;
    private final float mDefaultDarkenOpacity;

    private ImageView mMainImage;
    private FloatingActionButton mShuffleButton;
    private FloatingActionButton mRepeatButton;
    private View mBackgroundView;
    private BitmapDrawable mBlurredDrawable;
    private BitmapDrawable mBackgroundDrawable;
    private UUID mSongId;
    private boolean mLastSettingsToBlur;
    private float mLastSettingsDarkenOpacity;
    private Bitmap mCurrentBitmap;

    private Animation mSlideInAnimation1;
    private Animation mSlideInAnimation2;
    private Animation mSlideOutAnimation;

    private int mColorOn;
    private int mColorOff;

    private boolean mShuffleState;
    private boolean mRepeatState;

    public MusicPresenter(Context context) {
        super(context.getResources().getColor(R.color.music_main_background), Color.WHITE);
        mRS = RenderScript.create(context);

        mDefaultDarkenOpacity = Float.parseFloat(context.getString(R.string.settings_default_album_art_darken_opacity));
        mLastSettingsToBlur = shouldBlur(context);
        mLastSettingsDarkenOpacity = getArtDarkenOpacity(context);
    }

    @Override
    public View onAttachView(ViewGroup parent, SharedMainUI ui, SpeechCategory category) {
        synchronized (this) {
            if (mMainImage == null) {
                final Context ctx = parent.getContext();
                mBackgroundView = new FrameLayout(ctx);
                parent.addView(mBackgroundView);

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
    public void onShowPresenter(ViewGroup parent, SpeechCategory category) {
        super.onShowPresenter(parent, category);

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

    @Override
    public void onConfigurationChanged(Configuration newConfig, SharedMainUI ui) {
        if (mCurrentBitmap != null) {
            boolean shouldBlur = shouldBlur(ui.getContext());
            mBlurredDrawable = setBackgroundImage(mBackgroundView, mCurrentBitmap, shouldBlur);
            mBackgroundDrawable = setBackgroundImage(ui.getBackgroundView(), mCurrentBitmap, false);
        }
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

    public void updateMainUI(SharedMainUI ui, Song song, Bitmap bitmap) {
        mCurrentBitmap = bitmap;
        if (bitmap != null) {
            UUID id = song.getId();
            boolean shouldBlur = shouldBlur(ui.getContext());
            float darkenOpacity = getArtDarkenOpacity(ui.getContext());
            if (id != mSongId || mBlurredDrawable == null
                    || shouldBlur != mLastSettingsToBlur || darkenOpacity != mLastSettingsDarkenOpacity) {
                mSongId = id;
                mLastSettingsToBlur = shouldBlur;
                mLastSettingsDarkenOpacity = darkenOpacity;
                mBlurredDrawable = setBackgroundImage(mBackgroundView, bitmap, shouldBlur);
                mBackgroundDrawable = setBackgroundImage(ui.getBackgroundView(), bitmap, false);
            } else {
                mBackgroundView.setBackground(mBlurredDrawable);
                ui.setBackground(mBackgroundDrawable);
            }
        } else {
            ui.clearBackground();
            mBackgroundView.setBackground(null);
        }
        ui.setText(song.getTitle(), song.getArtist());
    }

    // Crops image to screen and then blurs if requested
    private BitmapDrawable setBackgroundImage(View backgroundView, Bitmap bitmap, boolean blur) {
        DisplayMetrics metrics = backgroundView.getContext().getApplicationContext().getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        float screenRatio = screenWidth * 1.0f / screenHeight;
        float imageRatio = bitmap.getWidth() * 1.0f / bitmap.getHeight();
        int w, h;
        Bitmap croppedImage;
        if (screenRatio < imageRatio) {
            w = Math.round(bitmap.getWidth() * screenRatio);
            h = bitmap.getHeight();
            croppedImage = Bitmap.createBitmap(bitmap, Math.abs(bitmap.getWidth() - w) / 2, 0, w, h);
        } else {
            w = bitmap.getWidth();
            h = Math.round(bitmap.getHeight() / screenRatio);
            croppedImage = Bitmap.createBitmap(bitmap, 0, Math.abs(bitmap.getHeight() - h) / 2, w, h);
        }

        if (blur) {
            croppedImage = Bitmap.createScaledBitmap(croppedImage, screenWidth / 4, screenHeight / 4, true);
            final Allocation input = Allocation.createFromBitmap(mRS, croppedImage);
            final Allocation output = Allocation.createTyped(mRS, input.getType());
            final ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(mRS, Element.U8_4(mRS));
            script.setRadius(8f);
            script.setInput(input);
            script.forEach(output);
            output.copyTo(croppedImage);
        }

        // Add 35% black overlay over the image
        Canvas canvas = new Canvas(croppedImage);
        canvas.drawColor(Color.argb((int)(255 * mLastSettingsDarkenOpacity), 0, 0, 0));

        BitmapDrawable image = new BitmapDrawable(backgroundView.getResources(), croppedImage);
        backgroundView.setBackground(image);
        return image;
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

    private boolean shouldBlur(Context ctx) {
        return !LazyPref.getBool(ctx, R.string.settings_music_art_blur_command_key);
    }

    private float getArtDarkenOpacity(Context ctx) {
        return LazyPref.getFloat(ctx, R.string.settings_music_art_opacity_key, mDefaultDarkenOpacity);
    }
}
