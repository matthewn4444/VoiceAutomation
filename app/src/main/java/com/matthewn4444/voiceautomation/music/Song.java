package com.matthewn4444.voiceautomation.music;

import java.io.File;
import java.util.UUID;

public class Song {
    private final UUID mId;
    private final String mTitle;
    private final String mArtist;
    private final File mPath;
    private final String mAlbum;
    private final int mDuration;
    private final long mArtId;

    public Song(String title, String artist, String path, String album, int duration, long albumId) {
        mTitle = title;
        mArtist = artist;
        mPath = new File(path);
        mAlbum = album;
        mDuration = duration;
        mArtId = albumId;
        mId = UUID.randomUUID();
    }

    public UUID getId() {
        return mId;
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
