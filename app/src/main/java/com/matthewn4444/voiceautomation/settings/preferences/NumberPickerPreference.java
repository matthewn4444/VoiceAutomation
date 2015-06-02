package com.matthewn4444.voiceautomation.settings.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import com.matthewn4444.voiceautomation.R;

public class NumberPickerPreference extends DialogPreference {
    public static final int DEFAULT_MIN_VALUE = 0;
    public static final int DEFAULT_MAX_VALUE = 100;

    private final int mMinValue;
    private final int mMaxValue;
    private int mValue;

    private NumberPicker mNumberPicker;

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerPreference, 0, 0);
        mMaxValue = a.getInteger(R.styleable.NumberPickerPreference_max, DEFAULT_MAX_VALUE);
        mMinValue = a.getInteger(R.styleable.NumberPickerPreference_min, DEFAULT_MIN_VALUE);
        a.recycle();

        generateNumberPicker();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        setValue(restorePersistedValue ? getPersistedInt(0) : (int) defaultValue);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mNumberPicker.getParent() != null) {
            ((ViewGroup)mNumberPicker.getParent()).removeView(mNumberPicker);
        }
        super.onDismiss(dialog);
    }

    @Override
    protected View onCreateDialogView() {
        setValue(mValue);
        return mNumberPicker;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult && shouldPersist()) {
            int n = mNumberPicker.getValue();
            mValue = n;
            persistInt(n);
        }
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

    public int getValue() {
        return mValue;
    }

    private void setValue(int value) {
        mValue = value;
        mNumberPicker.setValue(value);
    }

    private NumberPicker generateNumberPicker() {
        mNumberPicker = new NumberPicker(getContext());
        mNumberPicker.setMinValue(mMinValue);
        mNumberPicker.setMaxValue(mMaxValue);
        mNumberPicker.setWrapSelectorWheel(false);
        return mNumberPicker;
    }


    private static class SavedState extends BaseSavedState {
        int value;

        public SavedState(Parcel source) {
            super(source);
            value = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(value);
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

