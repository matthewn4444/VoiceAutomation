package com.matthewn4444.voiceautomation;

import com.matthewn4444.voiceautomation.SpeechController.PartialReturnResult;
import com.matthewn4444.voiceautomation.SpeechController.SpeechModel;

public abstract class SpeechCategory {
    public final static String DefaultThreshold = "1e-1";
    public final static int DefaultTimeout = 10000;

    private final String mThreshold;
    private final String mActivationPhrase;
    private final SpeechModel mModel;
    private final String mMessage;
    private final String mAssetsGrammerFile;
    private final int mTimeout;
    private final ICategoryPresenter mPresenter;

    public static interface ICategoryPresenter {
        public void animateBackgroundColor(int to);
        public void animateCaptionColor(int to);
        public void animateMainImageOpacity(float to);
    }

    public SpeechCategory(ICategoryPresenter presenter, String activationPhrase, String assetsGrammerFile, SpeechModel model, String message) {
        this(presenter, activationPhrase, assetsGrammerFile, model, message, DefaultThreshold);
    }

    public SpeechCategory(ICategoryPresenter presenter, String activationPhrase, String assetsGrammerFile, SpeechModel model, String message, String threshold) {
        this(presenter, activationPhrase, assetsGrammerFile, model, message, DefaultThreshold, DefaultTimeout);
    }

    public SpeechCategory(ICategoryPresenter presenter, String activationPhrase, String assetsGrammerFile, SpeechModel model, String message, String threshold, int timeout) {
        mActivationPhrase = activationPhrase;
        mAssetsGrammerFile = assetsGrammerFile;
        mModel = model;
        mMessage = message;
        mThreshold = threshold;
        mTimeout = timeout;
        mPresenter = presenter;
    }

    public abstract void onResult(String result);

    public abstract boolean isAvailable();

    public abstract int getMainResDrawable();

    public abstract int getBackResDrawable();

    public abstract float getMainImageOpacity();

    public abstract int getMainColor();

    public abstract int getMainTextColor();

    public String getGrammerFileName() {
        return mAssetsGrammerFile;
    }

    public PartialReturnResult filterPartialResult(PartialReturnResult result) {
        return result;
    }

    public void pause() {}

    public void resume() {}

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

    public int getTimeout() {
        return mTimeout;
    }

    protected ICategoryPresenter getPresenter() {
        return mPresenter;
    }
}
