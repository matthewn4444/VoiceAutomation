package com.matthewn4444.voiceautomation;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.HashMap;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

public class SpeechController implements RecognitionListener {
    private static final String TAG = "SpeechController";

    private static final String CommandFileName = "commands.gram";
    private static final String KWS_SEARCH = "wakeup";

    public static enum SpeechModel {
        DEFAULT, PHONETIC, LANGUAGE
    };

    private final SpeechCategory[] mCategories;
    private final Context mCtx;
    private final HashMap<String, SpeechCategory> mLookup;

    private SpeechRecognizer mRecognizer;
    private SpeechListener mListener;
    private SpeechCategory mCurrentCategory;
    private Handler mDelay = new Handler();
    private File mCommandFile;
    private boolean mIsReady;
    private int mTimeout;

    private SoundPool mSoundPool;
    private int mSoundStartId;
    private int mSoundResultId;
    private int mSoundFailId;

    public class PartialReturnResult {
        public boolean isFinished = false;
        public String filteredText;

        public PartialReturnResult(String text) {
            filteredText = text;
        }
    }

    public interface SpeechListener {
        public void onSpeechReady();
        public void onSpeechError(Exception e);
        public void onBeginSpeechCategory(SpeechCategory category);
        public void onPartialResult(String text);
        public void onSpeechResult(String text);
    }

    public SpeechController(Context c, SpeechCategory[] categories) {
        this(c, categories, c.getResources().getInteger(R.integer.settings_default_speech_timeout));
    }

    public SpeechController(Context ctx, SpeechCategory[] categories, int speechTimeout) {
        mCtx = ctx;
        mCategories = categories;
        mLookup = new HashMap<>();
        mIsReady = false;
        mTimeout = speechTimeout;

        for (SpeechCategory cate: categories) {
            mLookup.put(cate.getActivationCommand(), cate);
        }

        // Initialize the recognition software
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                // Prepare speech recognition files
                try {
                    mCommandFile = generateCommandsFile();
                    Assets assets = new Assets(mCtx);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                mIsReady = true;
                if (result != null) {
                    if (mListener != null) {
                        mListener.onSpeechError(result);
                    }
                } else {
                    switchSearch(KWS_SEARCH);
                    if (mListener != null) {
                        mListener.onSpeechReady();
                    }
                }
            }
        }.execute();
    }

    @Override
    public void onBeginningOfSpeech() {
        mCurrentCategory = mLookup.get(mRecognizer.getSearchName());
    }

    @Override
    public void onEndOfSpeech() {
        if (!mRecognizer.getSearchName().equals(KWS_SEARCH))
            switchSearch(KWS_SEARCH);
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr().trim();
        Log.v(TAG, "Partial result: " + text);

        SpeechCategory cate = mLookup.get(text);
        if (cate != null) {
            switchSearch(text);
        } else {
            if (mCurrentCategory != null) {
                PartialReturnResult res = new PartialReturnResult(text);
                res = mCurrentCategory.filterPartialResult(res);
                text = res.filteredText;

                // The category filter told that search is finished
                if (res.isFinished) {
                    mRecognizer.cancel();
                    speechFinishedWithResult(text);
                    switchSearch(KWS_SEARCH);
                    return;
                }
            }
            if (mListener != null) {
                mListener.onPartialResult(text);
            }
        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            speechFinishedWithResult(text);
            mCurrentCategory = null;
        } else {
            speechFinishedWithResult(null);
        }
    }

    private void speechFinishedWithResult(String text) {
        if (mCurrentCategory != null) {
            if (text != null) {
                playSoundEffect(mSoundResultId);
            }
            mCurrentCategory.onResult(text);
        }
        if (text == null) {
            playSoundEffect(mSoundFailId);
        }
        if (mListener != null) {
            mListener.onSpeechResult(text);
        }
    }

    private void playSoundEffect(int id) {
        if (mSoundPool != null) {
            mSoundPool.play(id, 1, 1, 0, 1, 1);
        }
    }

    @Override
    public void onError(Exception e) {
        if (mListener != null) {
            mListener.onSpeechError(e);
        }
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }

    public void shutdown() {
        mRecognizer.cancel();
        mRecognizer.shutdown();
    }

    private void switchSearch(final String searchName) {
        mRecognizer.stop();

        // If we are not spotting, start listening with timeout
        Log.v(TAG, "Switch search to " + searchName);
        if (searchName.equals(KWS_SEARCH)) {
            mRecognizer.startListening(searchName);
            if (mListener != null) {
                mListener.onBeginSpeechCategory(null);
            }
        } else {
            playSoundEffect(mSoundStartId);
            final SpeechCategory cate = mLookup.get(searchName);
            if (cate != null) {
                mDelay.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mRecognizer.startListening(searchName, mTimeout);
                        if (mListener != null) {
                            mListener.onBeginSpeechCategory(cate);
                        }
                    }
                }, 250);
            }
        }
    }

    public void pause() {
        Log.v(TAG, "Pause speech recognition");
        if (mRecognizer != null) {
            mRecognizer.stop();
        }
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }
    }

    public void resume() {
        if (mIsReady) {
            Log.v(TAG, "Resume speech recognition");
            switchSearch(KWS_SEARCH);
        }
        setupSoundEffects();
    }

    public void setSpeechListener(SpeechListener listener) {
        mListener = listener;
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        mRecognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                        // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setRawLogDir(assetsDir)

                        // Threshold to tune for keyphrase to balance between false alarms and misses
                .setKeywordThreshold(1e-45f)

                        // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)

                .getRecognizer();
        mRecognizer.addListener(this);

        // Create keyword-activation search.
        mRecognizer.addKeywordSearch(KWS_SEARCH, mCommandFile);

        // Add grammar searches
        for (SpeechCategory cate: mCategories) {
            String command = cate.getActivationCommand();
            File grammerFile = new File(assetsDir, cate.getGrammerFileName());
            switch (cate.getModelType()) {
                case DEFAULT:
                    mRecognizer.addGrammarSearch(command, grammerFile);
                    break;
                case LANGUAGE:
                    mRecognizer.addNgramSearch(command, grammerFile);
                    break;
                case PHONETIC:
                    mRecognizer.addAllphoneSearch(command, grammerFile);
                    break;
                default:
                    throw new InvalidParameterException("Invalid speech model was specified");
            }
        }
    }

    private File generateCommandsFile() throws IOException {
        File commandFile = null;
        FileOutputStream writeStream = null;
        // Create the speech file
        try {
            writeStream = mCtx.openFileOutput(CommandFileName, Context.MODE_PRIVATE);
            for (SpeechCategory cate: mCategories) {
                writeStream.write((cate.getCommandGrammerLine() + "\n").getBytes());
            }
            commandFile = new File(mCtx.getFilesDir() + "/" + CommandFileName);
        } catch(IOException e) {
            throw e;
        } finally {
            if (writeStream != null) {
                try {
                    writeStream.close();
                } catch (Exception e) {}
            }
        }
        return commandFile;
    }

    private void setupSoundEffects() {
        if (mSoundPool == null) {
            mSoundPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
            mSoundStartId = mSoundPool.load(mCtx, R.raw.listen_start, 1);
            mSoundResultId = mSoundPool.load(mCtx, R.raw.listen_result, 1);
            mSoundFailId = mSoundPool.load(mCtx, R.raw.listen_fail, 1);
        }
    }
}
