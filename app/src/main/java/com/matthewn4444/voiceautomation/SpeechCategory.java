package com.matthewn4444.voiceautomation;

import com.matthewn4444.voiceautomation.SpeechController.PartialReturnResult;
import com.matthewn4444.voiceautomation.SpeechController.SpeechModel;

public class SpeechCategory {
    public final static String DefaultThreshold = "1e-1";

    private final String mThreshold;
    private final String mActivationPhrase;
    private final SpeechModel mModel;
    private final String mMessage;
    private final String mAssetsGrammerFile;

    private OnSpeechResultListener mListener;

    public interface OnSpeechResultListener {
        public PartialReturnResult onParsePartialResult(SpeechCategory cate, PartialReturnResult result);
        public String onSpeechResult(SpeechCategory cate, String result);
    }

    public SpeechCategory(String activationPhrase, String assetsGrammerFile, SpeechModel model, String message) {
        this(activationPhrase, assetsGrammerFile, model, message, DefaultThreshold);
    }

    public SpeechCategory(String activationPhrase, String assetsGrammerFile, SpeechModel model, String message, String threshold) {
        mActivationPhrase = activationPhrase;
        mAssetsGrammerFile = assetsGrammerFile;
        mModel = model;
        mMessage = message;
        mThreshold = threshold;
    }

    public String getGrammerFileName() {
        return mAssetsGrammerFile;
    }

    public PartialReturnResult filterPartialResult(PartialReturnResult result) {
        if (mListener != null) {
            result = mListener.onParsePartialResult(this, result);
        }
        return result;
    }

    public void onResult(String result) {
        if (mListener != null) {
            mListener.onSpeechResult(this, result);
        }
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


    public void setOnSpeechResultListener(OnSpeechResultListener listener) {
        mListener = listener;
    }
}
