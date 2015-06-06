package com.matthewn4444.voiceautomation.music;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.matthewn4444.voiceautomation.CategoryPresenter;
import com.matthewn4444.voiceautomation.R;
import com.matthewn4444.voiceautomation.SpeechCategory;

public class MusicPresenter extends CategoryPresenter {
    private ImageView mMainImage;

    public MusicPresenter(Context context) {
        super(context.getResources().getColor(R.color.music_main_background), Color.WHITE);
    }

    @Override
    public View onAttachView(ViewGroup parent, TextView caption, SpeechCategory category) {
        synchronized (this) {
            if (mMainImage == null) {
                mMainImage = new ImageView(parent.getContext());
                mMainImage.setImageResource(R.drawable.music);
                parent.addView(mMainImage);
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
}
