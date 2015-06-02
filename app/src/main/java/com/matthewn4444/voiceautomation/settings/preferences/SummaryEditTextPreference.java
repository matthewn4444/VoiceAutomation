package com.matthewn4444.voiceautomation.settings.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;

import com.matthewn4444.voiceautomation.R;

public class SummaryEditTextPreference extends EditTextPreference {
    private final String mDefaultText;
    private final boolean mSummaryWasSet;

    private boolean mUseGetTextHack = false;
    private OnEmptyTextDialogShowListener mListener;

    public interface OnEmptyTextDialogShowListener {
        String OnEmptyTextDialogShow();
    }

    public SummaryEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SummaryEditTextPreference, 0, 0);
        mDefaultText = a.getString(R.styleable.SummaryEditTextPreference_emptyText);
        a.recycle();

        mSummaryWasSet = super.getSummary() != null;
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        setSummary(getSummary());
    }

    @Override
    protected void onBindDialogView(View view) {
        mUseGetTextHack = true;
        super.onBindDialogView(view);
        mUseGetTextHack = false;
    }

    @Override
    public String getText() {
        if (mUseGetTextHack) {
            String value = getPersistedString(null);
            if (!isValidText(value)) {
                if (mListener != null) {
                    return mListener.OnEmptyTextDialogShow();
                }
                return null;
            }
        }
        return super.getText();
    }

    @Override
    public CharSequence getSummary() {
        if (mSummaryWasSet) {
            return super.getSummary();
        }
        String value = getPersistedString(null);
        if (isValidText(value)) {
            return value;
        }
        return mDefaultText;
    }

    public void setOnEmptyTextDialogShowListener(OnEmptyTextDialogShowListener listener) {
        mListener = listener;
    }

    private boolean isValidText(String value) {
        return value != null && !value.trim().equals("");
    }
}
