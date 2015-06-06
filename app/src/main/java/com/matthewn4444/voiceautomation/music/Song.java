package com.matthewn4444.voiceautomation.music;

import java.io.File;

public class Song {
    private String mTitle;
    private String mArtist;
    private File mPath;
    private String mAlbum;
    private int mDuration;
    private long mArtId;

    public Song(String title, String artist, String path, String album, int duration, long albumId) {
        mTitle = title;
        mArtist = artist;
        mPath = new File(path);
        mAlbum = album;
        mDuration = duration;
        mArtId = albumId;
    }

    public String getTitla() {
        return mTitle;
    }

    public String getArtist() {
        return mArtist;
    }

    public File getPath() {
        return mPath;
    }

    public String getAlbum() {
        return mAlbum;
    }

    public int getDuration() {
        return mDuration;
    }

    public long getArtId() {
        return mArtId;
    }
}
