package com.matthewn4444.voiceautomation;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

public class LocationHelper implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "LocationHelper";
    private static final String HighAccuracyProvider = LocationManager.GPS_PROVIDER;
    private static final String LowAccuracyProvider = LocationManager.NETWORK_PROVIDER;

    private final Context mCtx;
    private final String mProvider;
    private final LocationManager mManager;

    private Location mLocation;
    private boolean mAskToEnableLocation;
    private OnLocationFoundListener mFoundListener;
    private GoogleApiClient mApiClient;

    public interface OnLocationFoundListener {
        void onLocationFound(Location location);
    }

    public LocationHelper(Context context) {
        this(context, false);
    }

    public LocationHelper(Context context, boolean useHighAccuracy) {
        mCtx = context;
        mProvider = useHighAccuracy ? HighAccuracyProvider : LowAccuracyProvider;
        mManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mAskToEnableLocation = false;

        mApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        if (hasLocationEnabled()) {
            mApiClient.connect();
        }
    }

    public static boolean isLocationHardcoded(Context context) {
        return LazyPref.getBool(context, R.string.setting_light_location_hardcode_location_key);
    }

    public boolean hasLocationEnabled() {
        return mManager.isProviderEnabled(mProvider);
    }

    public void openLocationSettings() {
        mAskToEnableLocation = true;
        mCtx.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    public void queryLocation(final OnLocationFoundListener listener) {
        // User selected to hardcode location, so we send back that data
        if (isLocationHardcoded(mCtx)) {
            listener.onLocationFound(getCacheLocation());
            return;
        }

        if (hasLocationEnabled()) {
            if (mLocation != null) {
                internalQueryLocation();
                listener.onLocationFound(mLocation);
            } else {
                mFoundListener = listener;
            }
        } else {
            new AlertDialog.Builder(mCtx)
                    .setMessage(R.string.prompt_ask_turn_on_location)
                    .setTitle(R.string.app_name)
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openLocationSettings();
                        }
                    }).show();
        }
    }

    private void internalQueryLocation() {
        try {
            Location location = LocationServices.FusedLocationApi.getLastLocation(mApiClient);
            if (location != null && location.getLatitude() != 0 && location.getLongitude() != 0) {
                mLocation = location;
                Log.i("lunch", "foudn location to " + location.getLatitude() + ", " + location.getLongitude());
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public void onPause() {
        if (mApiClient.isConnected()) {
            mApiClient.disconnect();
        }
    }

    public void onResume() {
        if (mAskToEnableLocation) {
            synchronized (this) {
                if (mAskToEnableLocation) {
                    mAskToEnableLocation = false;
                    if (hasLocationEnabled()) {
                        queryLocation(mFoundListener);
                    }
                }
            }
        }
        if (!mApiClient.isConnected() && hasLocationEnabled()) {
            mApiClient.connect();
        }
    }

    public Location getLastLocation() {
        if (isLocationHardcoded(mCtx)) {
            return getCacheLocation();
        }

        if (mLocation == null) {
            internalQueryLocation();
        }
        return mLocation;
    }

    public void cacheLocation() {
        if (mLocation != null) {
            double latitude = mLocation.getLatitude();
            double longitude = mLocation.getLongitude();
            if (latitude != 0 && longitude != 0) {
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mCtx);
                pref.edit()
                        .putString(mCtx.getString(R.string.settings_key_cached_latitude), latitude + "")
                        .putString(mCtx.getString(R.string.settings_key_cached_longitude), longitude + "")
                        .apply();
            }
        }
    }

    public Location getCacheLocation() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mCtx);
        String latitudeStr = pref.getString(mCtx.getString(R.string.settings_key_cached_latitude), null);
        String longitudeStr = pref.getString(mCtx.getString(R.string.settings_key_cached_longitude), null);

        if (latitudeStr == null && longitudeStr == null) {
            return null;
        } else {
            Location location = new Location("");
            location.setLatitude(  latitudeStr != null ? Float.parseFloat(latitudeStr)  : 0);
            location.setLongitude(longitudeStr != null ? Float.parseFloat(longitudeStr) : 0);
            return location;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        internalQueryLocation();
        if (mFoundListener != null) {
            mFoundListener.onLocationFound(mLocation);
            mFoundListener = null;
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (hasLocationEnabled()) {
            mApiClient.connect();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Geolocation connection failed " + connectionResult.getErrorCode());
    }
}
