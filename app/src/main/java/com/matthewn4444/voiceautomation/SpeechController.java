package com.matthewn4444.voiceautomation;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

public class SpeechController implements RecognitionListener {
    private static final String TAG = "SpeechController";

    private static final String CommandFileName = "commands.gram";
    private static final String LOCK_SEARCH = "lock";
    private static final String KWS_SEARCH = "command";

    private static final int SAME_PARTIAL_RESULT_TIMEOUT = 2000;

    public static enum SpeechModel {
        DEFAULT, PHONETIC, LANGUAGE
    };

    private final Context mCtx;
    private final HashMap<String, SpeechCategory> mCategories;
    private final AudioManager mAudioManager;

    private SpeechRecognizer mRecognizer;
    private SpeechListener mListener;
    private SpeechCategory mCurrentCategory;
    private Handler mDelay = new Handler();
    private Object mTimerLock = new Object();
    private Timer mTimer;
    private File mCommandFile;
    private boolean mIsReady;
    private boolean mIsLocked;
    private boolean mPaused;
    private String LOCK_PHRASE;
    private String LOCK_PHRASE1;
    private String LOCK_PHRASE2;
    private String UNLOCK_PHRASE;

    private int mPartialResultDiffCount;
    private long mPartialResultTimeLastChange;
    private String mLastPartialResult;

    private String mLastQuickCommand;
    private long mLastQuickCommandTime;

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
        public void onLock(boolean isLocked);
        public void onCategoryUnavailable(SpeechCategory category);
    }

    public SpeechController(Context ctx, HashMap<String, SpeechCategory> categories) {
        mCtx = ctx;
        mCategories = categories;
        mAudioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        mIsReady = false;
        mIsLocked = false;
        LOCK_PHRASE = mCtx.getString(R.string.command_default_lock);
        LOCK_PHRASE1 = mCtx.getString(R.string.command_default_lock1);
        LOCK_PHRASE2 = mCtx.getString(R.string.command_default_lock2);
        UNLOCK_PHRASE = mCtx.getString(R.string.command_default_unlock);

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
        mCurrentCategory = mCategories.get(mRecognizer.getSearchName());
    }

    @Override
    public void onEndOfSpeech() {
        if (!mRecognizer.getSearchName().equals(KWS_SEARCH) && !mIsLocked)
            switchSearch(KWS_SEARCH);
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr().trim();
        Log.v(TAG, "Partial result: " + text);

        SpeechCategory cate = mCategories.get(text);
        if (cate != null) {
            if (cate.isAvailable()) {
                switchSearch(text);
                mLastPartialResult = null;
                mPartialResultDiffCount = 0;
            } else {
                if (mListener != null) {
                    mListener.onCategoryUnavailable(cate);
                }
                endSpeech();
            }
        } else if (!mIsLocked && (text.equals(LOCK_PHRASE) || text.equals(LOCK_PHRASE1)
                || text.equals(LOCK_PHRASE2))) {
            switchSearch(LOCK_SEARCH);
        } else if (mIsLocked && text.equals(UNLOCK_PHRASE)) {
            mIsLocked = false;
            if (mListener != null) {
                mListener.onLock(false);
            }
            switchSearch(KWS_SEARCH);
        } else {
            if (mCurrentCategory != null) {
                PartialReturnResult res = new PartialReturnResult(text);
                res = mCurrentCategory.filterPartialResult(res);
                text = res.filteredText;

                // The category filter told that search is finished
                if (res.isFinished) {
                    endSpeech(text);
                    return;
                }
            } else {
                // Check quick commands for each category
                boolean isSimilarToLastTime = mLastQuickCommand != null && text.startsWith(mLastQuickCommand);
                long now = System.currentTimeMillis();
                if (!isSimilarToLastTime || (now - mLastQuickCommandTime > 3000)) {
                    mLastQuickCommandTime = now;
                    mLastQuickCommand = text;
                    for (String command: mCategories.keySet()) {
                        SpeechCategory category = mCategories.get(command);
                        if (category.onQuickCommand(text)) {
                            endSpeech();
                            return;
                        }
                    }
                }
            }
            if (mListener != null) {
                mListener.onPartialResult(text);
            }

            // Keep track of constantly changing partial results, if we exceed said amount, end speech
            if (mLastPartialResult == null || !text.startsWith(mLastPartialResult)) {
                mPartialResultDiffCount++;
                if (mPartialResultDiffCount >= LazyPref.getIntDefaultRes(mCtx,
                        R.string.settings_speech_partial_result_changed_key,
                        R.integer.settings_default_speech_max_partial_result_changed)) {
                    endSpeech();
                }
            }

            // When the partial result does not change for a couple of seconds, decide whether to select or end
            if (!text.equals(mLastPartialResult)) {
                mPartialResultTimeLastChange = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - mPartialResultTimeLastChange > SAME_PARTIAL_RESULT_TIMEOUT) {
                if (mPartialResultDiffCount == 1) {
                    // Found phrase first try but waiting too long might be voice command
                    onResult(hypothesis);
                    onTimeout();
                } else {
                    // End speech because it changed a couple of times and waiting too long - noise
                    endSpeech();
                }
            }
            mLastPartialResult = text;
        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        if (!mIsLocked && !mPaused) {
            if (hypothesis != null) {
                String text = hypothesis.getHypstr();
                if (!text.equals(UNLOCK_PHRASE)) {
                    speechFinishedWithResult(text);
                }
                mCurrentCategory = null;
            } else {
                speechFinishedWithResult(null);
            }
        }
    }

    private void speechFinishedWithResult(String text) {
        endTimeout();
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
        mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
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
        endTimeout();
        if (!mIsLocked)
            switchSearch(KWS_SEARCH);
    }

    public void shutdown() {
        endTimeout();
        if (mRecognizer != null) {
            mRecognizer.removeListener(this);
            mRecognizer.cancel();
            mRecognizer.shutdown();
            mRecognizer = null;
        }
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
        } else if (searchName.equals(LOCK_SEARCH)) {
            mIsLocked = true;
            mRecognizer.startListening(searchName);
            if (mListener != null) {
                mListener.onLock(true);
            }
        } else {
            playSoundEffect(mSoundStartId);
            final SpeechCategory cate = mCategories.get(searchName);
            if (cate != null) {
                mDelay.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
                        mRecognizer.startListening(searchName, getSubCommandTimeout());
                        if (mListener != null) {
                            mListener.onBeginSpeechCategory(cate);
                        }

                        // Noise timeout, the above timeout restarts at each sound input
                        synchronized (mTimerLock) {
                            endTimeout();
                            mTimer = new Timer();
                            mTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    endSpeech();
                                }
                            }, getNoiseTimeout());
                        }
                    }
                }, 250);
            }
        }
    }

    public void pause() {
        mPaused = true;
        Log.v(TAG, "Pause speech recognition");
        endSpeech();
        if (mRecognizer != null) {
            mRecognizer.stop();
        }
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }
    }

    public void resume() {
        mPaused = false;
        if (mIsReady) {
            Log.v(TAG, "Resume speech recognition");
            if (mIsLocked) {
                switchSearch(LOCK_SEARCH);
            } else {
                switchSearch(KWS_SEARCH);
            }
        }
        setupSoundEffects();
    }

    public void setSpeechListener(SpeechListener listener) {
        mListener = listener;
    }

    private int getSubCommandTimeout() {
        return 1000 * LazyPref.getIntDefaultRes(mCtx, R.string.settings_speech_timeout_key,
                R.integer.settings_default_speech_keyword_timeout_min);
    }

    private int getNoiseTimeout() {
        return 1000 * LazyPref.getIntDefaultRes(mCtx, R.string.settings_speech_noise_timeout_key,
                R.integer.settings_default_speech_noise_timeout_min);
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
        mRecognizer.addKeyphraseSearch(LOCK_SEARCH, UNLOCK_PHRASE);

        mRecognizer.addKeywordSearch(KWS_SEARCH, mCommandFile);

        // Add grammar searches and setup quick commands
        for (String command: mCategories.keySet()) {
            SpeechCategory cate = mCategories.get(command);
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
            for (String key: mCategories.keySet()) {
                SpeechCategory category = mCategories.get(key);
                writeStream.write((category.getCommandGrammerLine() + "\n").getBytes());

                // Add all the quick commands
                Command[] qCommands = category.getQuickCommands();
                if (qCommands != null) {
                    for (Command command: qCommands) {
                        writeStream.write((command.getCommand() + "\n").getBytes());
                    }
                }
            }

            // Add phrases to stop listening
            writeStream.write((LOCK_PHRASE + " /" + Command.DefaultThreshold + "/\n").getBytes());
            writeStream.write((LOCK_PHRASE1 + " /" + Command.DefaultThreshold + "/\n").getBytes());
            writeStream.write((LOCK_PHRASE2 + " /" + Command.DefaultThreshold + "/\n").getBytes());

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

    private void endTimeout() {
        synchronized (mTimerLock) {
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
        }
    }

    private void endSpeech() {
        endSpeech(null);
    }

    private void endSpeech(final String text) {
        endSpeech(text, KWS_SEARCH);
    }

    private void endSpeech(final String text, final String newSearch) {
        endTimeout();
        if (mRecognizer != null) {
            mRecognizer.cancel();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (mCurrentCategory != null) {
                        speechFinishedWithResult(text);
                    }
                    switchSearch(newSearch);
                }
            });
        }
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
