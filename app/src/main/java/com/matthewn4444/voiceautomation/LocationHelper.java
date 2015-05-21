package com.matthewn4444.voiceautomation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;

import com.google.android.gms.location.LocationRequest;

import java.util.concurrent.TimeUnit;

import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import rx.Subscription;
import rx.functions.Action1;

public class LocationHelper {
    private static final String HighAccuracyProvider = LocationManager.GPS_PROVIDER;
    private static final String LowAccuracyProvider = LocationManager.NETWORK_PROVIDER;
    private static final int SubscriptionTimeout = 10;      // 10 Sec

    private final Activity mActivity;
    private final String mProvider;
    private final LocationManager mManager;

    private Subscription mSubscription;
    private Location mLocation;
    private boolean mAskToEnableLocation;
    private OnLocationFoundListener mFoundListener;

    public interface OnLocationFoundListener {
        void onLocationFound(Location location);
    }

    public LocationHelper(Activity activity) {
        this(activity, false);
    }

    public LocationHelper(Activity activity, boolean useHighAccuracy) {
        mActivity = activity;
        mProvider = useHighAccuracy ? HighAccuracyProvider : LowAccuracyProvider;
        mManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        mAskToEnableLocation = false;
    }

    public boolean hasLocationEnabled() {
        return mManager.isProviderEnabled(mProvider);
    }

    public void openLocationSettings(Activity activity) {
        mAskToEnableLocation = true;
        activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    public void queryLocation(final OnLocationFoundListener listener) {
        mFoundListener = listener;
        if (hasLocationEnabled()) {
            internalQueryLocation();
        } else {
            new AlertDialog.Builder(mActivity)
                    .setMessage(R.string.prompt_ask_turn_on_location)
                    .setTitle(R.string.app_name)
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openLocationSettings(mActivity);
                        }
                    }).show();
        }
    }

    private void internalQueryLocation() {
        Location location = mManager.getLastKnownLocation(mProvider);
        if (location != null && location.getLatitude() != 0 && location.getLongitude() != 0) {
            mLocation = location;
            mFoundListener.onLocationFound(location);
            mFoundListener = null;
        } else {
            LocationRequest request = LocationRequest.create() //standard GMS LocationRequest
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setExpirationDuration(1000 * SubscriptionTimeout)
                    .setNumUpdates(1);

            ReactiveLocationProvider locationProvider = new ReactiveLocationProvider(mActivity);
            mSubscription = locationProvider.getUpdatedLocation(request)
                    .timeout(SubscriptionTimeout, TimeUnit.SECONDS)
                    .subscribe(new Action1<Location>() {
                        @Override
                        public void call(Location location) {
                            mSubscription.unsubscribe();
                            mSubscription = null;
                            mLocation = location;
                            mFoundListener.onLocationFound(location);
                            mFoundListener = null;
                        }
                    });
        }
    }

    public void onPause() {
        if (mSubscription != null && !mSubscription.isUnsubscribed()) {
            mSubscription.unsubscribe();
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
    }

    public Location getLastLocation() {
        if (mLocation != null) {
            return mLocation;
        } else {
            Location location = mManager.getLastKnownLocation(mProvider);
            if (location != null && location.getLatitude() != 0 && location.getLongitude() != 0) {
                mLocation = location;
                return location;
            } else {
                return null;
            }
        }
    }
}
