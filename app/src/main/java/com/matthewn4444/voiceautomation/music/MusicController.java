package com.matthewn4444.voiceautomation.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import com.matthewn4444.voiceautomation.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicController implements MediaPlayer.OnCompletionListener {
    private static final String TAG = "MusicController";

    public static final String[] MUSIC_PROJECTION = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.ALBUM_ID
    };

    private final List<Song> mSongList;
    private final SharedPreferences mPrefs;
    private final String mShuffleKey;
    private final String mRepeatKey;

    private MediaPlayer mMediaPlayer;
    private Context mCtx;
    private Song mCurrentSong;
    private int mCurrentSongPosition;

    // States
    private boolean mIsPlaying;
    private boolean mIsShuffleOn;
    private boolean mIsRepeatOn;
    private boolean mIsMute;
    private boolean mIsMusicReady;

    public MusicController(final Context ctx) {
        mCtx = ctx;
        mSongList = new ArrayList<>();
        mShuffleKey = ctx.getString(R.string.setings_music_controls_state_shuffle);
        mRepeatKey = ctx.getString(R.string.setings_music_controls_state_repeat);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        mCurrentSongPosition = 0;
        mIsMusicReady = false;

        mIsPlaying = mIsMute = false;
        mIsShuffleOn = mPrefs.getBoolean(mShuffleKey, false);
        mIsRepeatOn = mPrefs.getBoolean(mRepeatKey, false);

        // Load the music
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                Cursor cursor = ctx.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        MUSIC_PROJECTION, null, null, MediaStore.Audio.Media.TITLE);
                if (cursor == null || cursor.isClosed()) {
                    Log.e(TAG, "Cannot load music because cursor is invalid.");
                } else {
                    try {
                        if (cursor.moveToFirst()) {
                            while (!cursor.isAfterLast()) {
                                // Parse the cursor
                                String id = cursor.getString(0);
                                String title = cursor.getString(1);
                                String artist = cursor.getString(2);
                                String path = cursor.getString(3);
                                String album = cursor.getString(4);
                                int duration = cursor.getInt(5);
                                boolean isMusic = cursor.getInt(6) != 0;
                                long albumId = cursor.getLong(7);
                                if (isMusic) {
                                    Song song = new Song(title, artist, path, album, duration, albumId);
                                    mSongList.add(song);
                                }
                                cursor.moveToNext();
                            }
                        }
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                        return false;
                    } finally {
                        cursor.close();
                    }
                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean aVoid) {
                super.onPostExecute(aVoid);
                if (mSongList.isEmpty()) {
                    Log.v(TAG, "There is no music on this phone and therefore we cannot use music.");
                } else {
                    mIsMusicReady = true;
                }
            }
        }.execute();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        playNextSong();
    }

    public void play() {
        log("play");
        if (mCurrentSong == null) {
            playNextSong();
        } else {
            internalPlay();
        }
    }

    public void pause() {
        log("pause");
        internalPause();
    }

    public void shuffleAll() {
        log("shuffle all");
        freeMusicPlayer();
        internalShuffleList();
        mCurrentSongPosition = 0;
        playNextSong();
    }

    public void increaseVolume() {
        getAudioManager().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
    }

    public void decreaseVolume() {
        getAudioManager().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
    }

    public void setMute(boolean flag) {
        mIsMute = flag;
        getAudioManager().setStreamMute(AudioManager.STREAM_MUSIC, flag);
    }

    public void seek(int position) {
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo(position);
        }
    }

    public void playNextSong() {
        log("playNextSong");
        if (mCurrentSong != null) {
            mCurrentSongPosition++;
            if (mCurrentSongPosition >= mSongList.size()) {
                if (mIsRepeatOn) {
                    if (mIsShuffleOn) {
                        internalShuffleList();
                    }
                    mCurrentSongPosition = 0;
                } else {
                    // All songs are done so we end
                    freeMusicPlayer();
                    return;
                }
            }
        }
        prepareMusic(mSongList.get(mCurrentSongPosition));
    }

    public void playPreviousSong() {
        log("playPreviousSong");
        if (mCurrentSong == null || mCurrentSongPosition == 0) {
            if (mIsRepeatOn) {
                mCurrentSongPosition = mSongList.size() - 1;
            } else {
                // Since repeat is off, this function should do nothing
                return;
            }
        } else {
            mCurrentSongPosition--;
        }
        prepareMusic(mSongList.get(mCurrentSongPosition));
    }

    public void setShuffle(boolean on) {
        if (mIsShuffleOn != on) {
            mIsShuffleOn = on;
            mPrefs.edit().putBoolean(mShuffleKey, on).apply();
        }
    }

    public void setRepeat(boolean on) {
        if (mIsRepeatOn != on) {
            mIsRepeatOn = on;
            mPrefs.edit().putBoolean(mRepeatKey, on).apply();
        }
    }

    public boolean isPlaying() {
        return mIsPlaying;
    }

    public boolean isShuffleOn() {
        return mIsShuffleOn;
    }

    public boolean isRepeatOn() {
        return mIsRepeatOn;
    }

    public boolean isMute() {
        return mIsMute;
    }

    public boolean isAvailable() {
        return mIsMusicReady;
    }

    protected void freeMusicPlayer() {
        log("free player");
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentSong = null;
        }
    }

    protected void prepareMusic(Song song) {
        log("preparemusic");
        prepareMusic(song, true);
    }

    protected void prepareMusic(Song song, boolean playNow) {
        log("preparemusic2");
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            freeMusicPlayer();
        }

        // Play the song only if it exists on the phone!
        File path = song.getPath();
        if (path != null && path.exists()) {
            try {
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setOnCompletionListener(this);
                mMediaPlayer.setDataSource(path.getPath());
                mMediaPlayer.prepare();
                log("prepare");
            } catch (IOException | IllegalStateException | SecurityException | IllegalArgumentException e) {
                e.printStackTrace();
                freeMusicPlayer();
                log("error");
                return;
            }
        } else {
            Log.i("lunch", "Path does not exist: " + path.getPath());
        }
        mCurrentSong = song;
        if (playNow) {
            internalPlay();
        }
        log("plays song");
    }

    protected void internalPlay() {
        log("internal play");
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
            mIsPlaying = true;
        }
    }

    protected void internalPause() {
        log("internal pause");
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
            mIsPlaying = false;
        }
    }

    protected void internalShuffleList() {
        Collections.shuffle(mSongList);
    }

    protected AudioManager getAudioManager() {
        return (AudioManager) mCtx.getSystemService(Context.AUDIO_SERVICE);
    }

    public static void log(Object... txt) {
        String returnStr = "";
        int i = 1;
        int size = txt.length;
        if (size != 0) {
            returnStr = txt[0] == null ? "null" : txt[0].toString();
            for (; i < size; i++) {
                returnStr += ", "
                        + (txt[i] == null ? "null" : txt[i].toString());
            }
        }
        Log.i("lunch", returnStr);
    }

}
