package com.matthewn4444.voiceautomation.music;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import com.matthewn4444.voiceautomation.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MusicController implements MediaPlayer.OnCompletionListener {
    private static final String TAG = "MusicController";
    private static final Uri ART_CONTENT_URI = Uri.parse("content://media/external/audio/albumart");

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

    private OnStateChangedListener mListener;
    private MediaPlayer mMediaPlayer;
    private Context mCtx;
    private Song mCurrentSong;
    private int mCurrentSongPosition;

    // Album Art
    private Bitmap mCachedPrevAlbumArt;
    private Bitmap mCachedCurrentAlbumArt;
    private Bitmap mCachedNextAlbumArt;
    private Bitmap mCachedFirstShuffleAlbumArt;
    private Song mFirstSongInShuffle;

    // States
    private boolean mIsPlaying;
    private boolean mIsShuffleOn;
    private boolean mIsRepeatOn;
    private boolean mIsMute;
    private boolean mIsMusicReady;

    public interface OnStateChangedListener {
        public void onSongChanged(Song song);
        public void onPlayStateChange(boolean isPlaying);
    }

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

                    // Preload all potential next album arts
                    cacheNextAlbumArt();
                    cachePrevAlbumArt();
                    cacheFirstShuffledSong();
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
        playNextSong();
    }

    public void increaseVolume() {
        getAudioManager().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
    }

    public void decreaseVolume() {
        getAudioManager().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
    }

    public void setOnSongChangedListener(OnStateChangedListener listener) {
        mListener = listener;
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

        // Slide the album art down the chain
        mCachedPrevAlbumArt = mCachedCurrentAlbumArt;
        mCachedCurrentAlbumArt = mCachedNextAlbumArt;
        mCachedNextAlbumArt = null;     // temp
        prepareMusic(mSongList.get(mCurrentSongPosition));
        cacheNextAlbumArt();
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

        // Slide the images up the chain
        mCachedNextAlbumArt = mCachedCurrentAlbumArt;
        mCachedCurrentAlbumArt = mCachedPrevAlbumArt;
        mCachedPrevAlbumArt = null;     // temp
        prepareMusic(mSongList.get(mCurrentSongPosition));
        cachePrevAlbumArt();
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

    public Song getCurrentSong() {
        return mCurrentSong;
    }

    public Bitmap getPlayingAlbumArt() {
        return mCachedCurrentAlbumArt;
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
                mCachedCurrentAlbumArt = null;
                if (mListener != null) {
                    mListener.onSongChanged(null);
                }
                return;
            }
        } else {
            Log.i("lunch", "Path does not exist: " + path.getPath());
        }
        mCurrentSong = song;
        if (playNow) {
            internalPlay();
        }
        if (mListener != null) {
            mListener.onSongChanged(mCurrentSong);
        }
        log("plays song");
    }

    protected void internalPlay() {
        log("internal play");
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
            if (!mIsPlaying) {
                mIsPlaying = true;
                if (mListener != null) {
                    mListener.onPlayStateChange(true);
                }
            }
        }
    }

    protected void internalPause() {
        log("internal pause");
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
            if (mIsPlaying) {
                mIsPlaying = false;
                if (mListener != null) {
                    mListener.onPlayStateChange(false);
                }
            }
        }
    }

    protected void internalShuffleList() {
        mCurrentSongPosition = 0;

        // Remove first shuffle song from list, shuffle remaining and add first back on top
        mSongList.remove(mFirstSongInShuffle);
        Collections.shuffle(mSongList);
        mSongList.add(0, mFirstSongInShuffle);

        // Update the album arts now that we have shuffled, we make sure that next song runs after this
        mCachedCurrentAlbumArt = null;
        mCachedPrevAlbumArt = null;
        mCachedNextAlbumArt = mCachedFirstShuffleAlbumArt;

        // Cache the new previous song and first shuffle song
        cachePrevAlbumArt();
        cacheFirstShuffledSong();
    }

    protected AudioManager getAudioManager() {
        return (AudioManager) mCtx.getSystemService(Context.AUDIO_SERVICE);
    }

    protected Bitmap getAlbumArt(long id) {
        Bitmap image = null;
        if (id != 0) {
            Uri albumArtUri = ContentUris.withAppendedId(ART_CONTENT_URI, id);
            ContentResolver res = mCtx.getContentResolver();
            InputStream in = null;
            try {
                in = res.openInputStream(albumArtUri);
                image = BitmapFactory.decodeStream(in);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception e) {
                    }
                }
            }
        }
        return image;
    }

    private void cacheNextAlbumArt() {
        // Preload next one
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                long id = mSongList.get(mCurrentSong != null || (mCurrentSongPosition + 1 >= mSongList.size())
                        ? mCurrentSongPosition + 1 : mCurrentSongPosition).getArtId();
                mCachedNextAlbumArt = getAlbumArt(id);
                return null;
            }
        }.execute();
    }

    private void cachePrevAlbumArt() {
        // Preload prev one
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                long id;
                if (mCurrentSong == null || mCurrentSongPosition == 0) {
                    if (mIsRepeatOn) {
                        id = mSongList.get(mSongList.size() - 1).getArtId();
                    } else {
                        // Since repeat is off, this function should do nothing
                        return null;
                    }
                } else {
                    id = mSongList.get(mCurrentSongPosition - 1).getArtId();
                }
                mCachedPrevAlbumArt = getAlbumArt(id);
                return null;
            }
        }.execute();
    }

    private void cacheFirstShuffledSong() {
        // Handle album art for random song
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                mFirstSongInShuffle = mSongList.get(new Random().nextInt(mSongList.size()));
                mCachedFirstShuffleAlbumArt = getAlbumArt(mFirstSongInShuffle.getArtId());
                return null;
            }
        }.execute();
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
