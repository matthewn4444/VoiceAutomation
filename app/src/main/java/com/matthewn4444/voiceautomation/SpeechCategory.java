package com.matthewn4444.voiceautomation;

import android.content.Context;
import android.preference.PreferenceManager;

import com.matthewn4444.voiceautomation.SpeechController.PartialReturnResult;
import com.matthewn4444.voiceautomation.SpeechController.SpeechModel;

public abstract class SpeechCategory {
    public final static int NO_PRIORITY = -1;

    private final Context mCtx;
    private final SpeechModel mModel;
    private final String mMessage;
    private final String mAssetsGrammerFile;
    private final CategoryPresenter mPresenter;
    private final String mSettingsActivationKey;
    private final String mSettingsDefaultCommand;
    private final Command mActivationCommand;

    private OnUIStateChangedListener mListener;


    public interface OnUIStateChangedListener {
        public void onUIStateChanged(SpeechCategory category);
    }

    public SpeechCategory(Context context, CategoryPresenter presenter, String defaultActivationCommand,
                          String settingsActivationKey, String assetsGrammerFile, SpeechModel model, String message) {
        this(context, presenter, defaultActivationCommand, settingsActivationKey, assetsGrammerFile, model, message, null);
    }

    public SpeechCategory(Context context, CategoryPresenter presenter, String defaultActivationCommand,
                          String settingsActivationKey, String assetsGrammerFile, SpeechModel model, String message, String threshold) {
        mCtx = context;
        mSettingsActivationKey = settingsActivationKey;
        mAssetsGrammerFile = assetsGrammerFile;
        mModel = model;
        mMessage = message;
        mPresenter = presenter;
        mSettingsDefaultCommand = defaultActivationCommand;

        mActivationCommand = new Command(null, threshold);
        updateActivationCommandFromSettings();
    }

    public abstract void onResult(String result);

    public abstract boolean isAvailable();

    public boolean onQuickCommand(String command) {
        return false;
    }

    public Command[] getQuickCommands() {
        return null;
    }

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
        return mActivationCommand.getGrammerLine();
    }

    public String getActivationCommand() {
        return mActivationCommand.getCommand();
    }

    public Context getContext() {
        return mCtx;
    }

    protected String getString(int key) {
        return mCtx.getString(key);
    }

    protected void stateInvalidated() {
        if (mListener != null && getUIPriority() != NO_PRIORITY) {
            mListener.onUIStateChanged(this);
        }
    }

    private void updateActivationCommandFromSettings() {
        mActivationCommand.setCommand(PreferenceManager.getDefaultSharedPreferences(mCtx)
                .getString(mSettingsActivationKey, mSettingsDefaultCommand));
    }
}
