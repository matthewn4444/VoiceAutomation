package com.matthewn4444.voiceautomation;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class LocationHelper implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final String TAG = "LocationHelper";
    private static final String HighAccuracyProvider = LocationManager.GPS_PROVIDER;
    private static final String LowAccuracyProvider = LocationManager.NETWORK_PROVIDER;
    private static final int DefaultLocationUpdateInterval = 4 * 1000;

    private final Context mCtx;
    private final String mProvider;
    private final LocationManager mManager;

    private boolean mAskToEnableLocation;
    private LocationListener mPendingListener;
    private LocationListener mClientListener;
    private GoogleApiClient mApiClient;
    private LocationRequest mRequest;

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

        mRequest = new LocationRequest();
        mRequest.setPriority(useHighAccuracy ? LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY : LocationRequest.PRIORITY_LOW_POWER);
        mRequest.setInterval(DefaultLocationUpdateInterval);
    }

    public boolean hasLocationEnabled() {
        return mManager.isProviderEnabled(mProvider);
    }

    public void openLocationSettings() {
        mAskToEnableLocation = true;
        mCtx.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    public void queryLocation(LocationListener listener) {
        queryLocation(listener, false);
    }

    public void queryLocation(LocationListener listener, boolean silent) {
        if (listener != null) {
            // If user has location setup
            if (!hasLocationEnabled()) {
                if (silent) {
                    // Only for automation we will not prompt the user to turn on location, return null
                    listener.onLocationChanged(null);
                } else {
                    // Show the dialog
                    mPendingListener = listener;
                    new AlertDialog.Builder(mCtx)
                            .setMessage(R.string.prompt_ask_turn_on_location)
                            .setTitle(R.string.app_name)
                            .setCancelable(false)
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mPendingListener = null;
                                }
                            })
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    openLocationSettings();
                                }
                            }).show();
                }
            } else if (ActivityCompat.checkSelfPermission(mCtx, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(mCtx, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                // Must have permissions to access location
                mPendingListener = listener;
                if (mApiClient.isConnected()) {
                    LocationServices.FusedLocationApi.requestLocationUpdates(mApiClient, mRequest, this);
                    mClientListener = listener;
                } else {
                    // Not connected yet, wait for it to be ready...
                    mPendingListener = listener;
                }
            } else {
                listener.onLocationChanged(null);
            }
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
                        LocationListener listener = mPendingListener;
                        mPendingListener = null;
                        queryLocation(listener);
                    }
                }
            }
        }
        if (!mApiClient.isConnected() && hasLocationEnabled()) {
            mApiClient.connect();
        }
    }

    public Location getLastLocation() {
        if (hasLocationEnabled()) {
            try {
                Location location = LocationServices.FusedLocationApi.getLastLocation(mApiClient);
                if (location != null && location.getLatitude() != 0 && location.getLongitude() != 0) {
                    return location;
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mApiClient, this);
            if (mClientListener != null) {
                mClientListener.onLocationChanged(location);
                mClientListener = null;
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (mPendingListener != null) {
            LocationListener listener = mPendingListener;
            mPendingListener = null;
            queryLocation(listener);
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
