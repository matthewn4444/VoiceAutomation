package com.matthewn4444.voiceautomation.settings.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.matthewn4444.voiceautomation.R;

public class SliderPreference extends DialogPreference {
    protected final static int SEEKBAR_RESOLUTION = 10000;

    private final boolean mSummaryWasSet;
    private final String mSuffix;
    private final String mLabel;

    protected float mValue;
    protected int mSeekBarValue;

    public SliderPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public SliderPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setDialogLayoutResource(R.layout.slider_preference_dialog);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SliderPreference, 0, 0);
        mSuffix = a.getString(R.styleable.SliderPreference_suffix);
        mLabel = a.getString(R.styleable.SliderPreference_label);
        a.recycle();

        mSummaryWasSet = super.getSummary() != null;

        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getFloat(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedFloat(mValue) : (Float) defaultValue);
    }

    @Override
    public CharSequence getSummary() {
        if (mSummaryWasSet) {
            return super.getSummary();
        }
        if (mSuffix != null) {
            return Integer.toString((int)(mValue * 100), 10) + mSuffix;
        } else {
            return Integer.toString((int)(mValue * 100), 10);
        }
    }

    public float getValue() {
        return mValue;
    }

    public void setValue(float value) {
        value = Math.max(0, Math.min(value, 1)); // clamp to [0, 1]
        if (shouldPersist()) {
            persistFloat(value);
        }
        if (value != mValue) {
            mValue = value;
            notifyChanged();
        }
    }

    @Override
    protected View onCreateDialogView() {
        View view = super.onCreateDialogView();
        final TextView valueTextView = (TextView) view.findViewById(android.R.id.text1);
        valueTextView.setText((int) (mValue * 100) + "");

        if (mLabel != null) {
            ((TextView) view.findViewById(R.id.label)).setText(mLabel);
        } else {
            view.findViewById(R.id.label).setVisibility(View.GONE);
        }
        ((TextView) view.findViewById(R.id.textSuffix)).setText(mSuffix);

        mSeekBarValue = (int) (mValue * SEEKBAR_RESOLUTION);
        SeekBar seekbar = (SeekBar) view.findViewById(R.id.slider_preference_seekbar);
        seekbar.setMax(SEEKBAR_RESOLUTION);
        seekbar.setProgress(mSeekBarValue);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    SliderPreference.this.mSeekBarValue = progress;
                    valueTextView.setText(progress * 100 / SEEKBAR_RESOLUTION + "");
                }
            }
        });
        return view;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        final float newValue = (float) mSeekBarValue / SEEKBAR_RESOLUTION;
        if (positiveResult && callChangeListener(newValue)) {
            setValue(newValue);
        }
        super.onDialogClosed(positiveResult);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.value = getValue();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }

    private static class SavedState extends BaseSavedState {
        float value;

        public SavedState(Parcel source) {
            super(source);
            value = source.readFloat();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeFloat(value);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}

