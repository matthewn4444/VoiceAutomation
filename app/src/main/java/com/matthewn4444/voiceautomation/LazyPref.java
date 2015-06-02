package com.matthewn4444.voiceautomation;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class LazyPref {
    static private SharedPreferences sPref;

    private LazyPref(){}

    static public String getString(Context ctx, int key) {
        return getString(ctx, key, null);
    }

    static public String getString(Context ctx, int key, String defaultValue) {
        return pref(ctx).getString(ctx.getString(key), defaultValue);
    }

    static public int getInt(Context ctx, int key) {
        return getInt(ctx, key, 0);
    }

    static public int getInt(Context ctx, int key, int defaultValue) {
        return pref(ctx).getInt(ctx.getString(key), defaultValue);
    }

    static public int getIntDefaultRes(Context ctx, int key, int defaultRes) {
        return pref(ctx).getInt(ctx.getString(key), ctx.getResources().getInteger(defaultRes));
    }

    static public float getFloat(Context ctx, int key) {
        return getFloat(ctx, key, 0);
    }

    static public float getFloat(Context ctx, int key, float defaultValue) {
        return pref(ctx).getFloat(ctx.getString(key), defaultValue);
    }

    static public long getLong(Context ctx, int key) {
        return getLong(ctx, key, 0);
    }

    static public long getLong(Context ctx, int key, long defaultValue) {
        return pref(ctx).getLong(ctx.getString(key), defaultValue);
    }

    static public boolean getBool(Context ctx, int key) {
        return getBool(ctx, key, false);
    }

    static public boolean getBool(Context ctx, int key, boolean defaultValue) {
        return pref(ctx).getBoolean(ctx.getString(key), defaultValue);
    }

    static private SharedPreferences pref(Context ctx) {
        if (sPref == null) {
            sPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        }
        return sPref;
    }
}
