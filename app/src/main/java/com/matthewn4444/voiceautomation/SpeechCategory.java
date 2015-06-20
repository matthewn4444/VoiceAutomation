package com.matthewn4444.voiceautomation;

import android.content.Context;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;

import com.matthewn4444.voiceautomation.SpeechController.PartialReturnResult;
import com.matthewn4444.voiceautomation.SpeechController.SpeechModel;

public abstract class SpeechCategory {
    public final static String DefaultThreshold = "1e-1";
    public final static int NO_PRIORITY = -1;
    public final static int DefaultTimeout = 10000;

    private final Context mCtx;
    private final String mThreshold;
    private final SpeechModel mModel;
    private final String mMessage;
    private final String mAssetsGrammerFile;
    private final CategoryPresenter mPresenter;
    private final String mSettingsActivationKey;
    private final String mSettingsDefaultCommand;

    private OnUIStateChangedListener mListener;

    private String mActivationPhrase;

    public interface OnUIStateChangedListener {
        public void onUIStateChanged(SpeechCategory category);
    }

    public SpeechCategory(Context context, CategoryPresenter presenter, String defaultActivationCommand,
                          String settingsActivationKey, String assetsGrammerFile, SpeechModel model, String message) {
        this(context, presenter, defaultActivationCommand, settingsActivationKey, assetsGrammerFile, model, message, DefaultThreshold);
    }

    public SpeechCategory(Context context, CategoryPresenter presenter, String defaultActivationCommand,
                          String settingsActivationKey, String assetsGrammerFile, SpeechModel model, String message, String threshold) {
        mCtx = context;
        mSettingsActivationKey = settingsActivationKey;
        mAssetsGrammerFile = assetsGrammerFile;
        mModel = model;
        mMessage = message;
        mThreshold = threshold;
        mPresenter = presenter;
        mSettingsDefaultCommand = defaultActivationCommand;

        updateActivationCommandFromSettings();
    }

    public abstract void onResult(String result);

    public abstract boolean isAvailable();

    public boolean updateAndHasActivationCommand() {
        String oldCommand = getActivationCommand();
        updateActivationCommandFromSettings();
        String newCommand = getActivationCommand();
        return !oldCommand.equals(newCommand);
    }

    public CategoryPresenter getPresenter() {
        return mPresenter;
    }

    public String getGrammerFileName() {
        return mAssetsGrammerFile;
    }

    public PartialReturnResult filterPartialResult(PartialReturnResult result) {
        return result;
    }

    public int getUIPriority() {
        return NO_PRIORITY;
    }

    public void pause() {}

    public void resume() {}

    public void setOnStateChangedListener(OnUIStateChangedListener listener) {
        mListener = listener;
    }

    public void handleMainUI(SharedMainUI ui) {
    }

    public SpeechModel getModelType() {
        return mModel;
    }

    public String getMessage() {
        return mMessage;
    }

    public String getCommandGrammerLine() {
        return mActivationPhrase + " /" + mThreshold + "/";
    }

    public String getActivationCommand() {
        return mActivationPhrase;
    }

    public Context getContext() {
        return mCtx;
    }

    protected String getString(int key) {
        return mCtx.getString(key);
    }

    protected void stateInvalidated() {
        if (mListener != null) {
            mListener.onUIStateChanged(this);
        }
    }

    private void updateActivationCommandFromSettings() {
        mActivationPhrase = PreferenceManager.getDefaultSharedPreferences(mCtx)
                .getString(mCtx.getString(R.string.settings_general_light_activation_command_key),
                        mSettingsDefaultCommand);
    }
}
