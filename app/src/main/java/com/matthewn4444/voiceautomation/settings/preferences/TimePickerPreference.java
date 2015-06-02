package com.matthewn4444.voiceautomation.settings.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

import com.matthewn4444.voiceautomation.R;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class TimePickerPreference extends DialogPreference {
    private final long mDefaultTime;

    private Calendar mCalendar;
    private TimePicker mPicker = null;

    public TimePickerPreference(Context context) {
        this(context, null);
    }

    public TimePickerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public TimePickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TimePickerPreference, 0, 0);
        int defaultHour = a.getInteger(R.styleable.TimePickerPreference_defaultHour, 0);
        int defaultMinute = a.getInteger(R.styleable.TimePickerPreference_defaultMin, 0);
        a.recycle();

        // Set the default time specified from the xml
        mCalendar = new GregorianCalendar();
        mCalendar.set(Calendar.HOUR_OF_DAY, defaultHour);
        mCalendar.set(Calendar.MINUTE, defaultMinute);
        mDefaultTime = mCalendar.getTimeInMillis();
    }

    @Override
    protected View onCreateDialogView() {
        mPicker = new TimePicker(getContext());
        return (mPicker);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        mPicker.setCurrentHour(mCalendar.get(Calendar.HOUR_OF_DAY));
        mPicker.setCurrentMinute(mCalendar.get(Calendar.MINUTE));
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            mCalendar.set(Calendar.HOUR_OF_DAY, mPicker.getCurrentHour());
            mCalendar.set(Calendar.MINUTE, mPicker.getCurrentMinute());

            setSummary(getSummary());
            if (callChangeListener(mCalendar.getTimeInMillis())) {
                persistLong(mCalendar.getTimeInMillis());
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return (a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            if (defaultValue == null) {
                mCalendar.setTimeInMillis(getPersistedLong(mDefaultTime));
            } else {
                mCalendar.setTimeInMillis(Long.parseLong(getPersistedString((String) defaultValue)));
            }
        } else {
            if (defaultValue == null) {
                mCalendar.setTimeInMillis(mDefaultTime);
            } else {
                mCalendar.setTimeInMillis(Long.parseLong((String) defaultValue));
            }
        }
        setSummary(getSummary());
    }

    @Override
    public CharSequence getSummary() {
        if (mCalendar == null) {
            return null;
        }
        return DateFormat.getTimeFormat(getContext()).format(new Date(mCalendar.getTimeInMillis()));
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.value = mCalendar.getTimeInMillis();
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
        mCalendar.setTimeInMillis(myState.value);
    }

    private static class SavedState extends BaseSavedState {
        long value;

        public SavedState(Parcel source) {
            super(source);
            value = source.readLong();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeLong(value);
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
