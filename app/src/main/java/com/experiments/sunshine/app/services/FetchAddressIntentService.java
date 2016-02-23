package com.experiments.sunshine.app.services;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.experiments.sunshine.app.Constants;
import com.experiments.sunshine.app.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created on 18/2/16.
 */
public class FetchAddressIntentService extends IntentService {
    private static final String TAG = FetchAddressIntentService.class.getSimpleName();

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     */
    public FetchAddressIntentService() {
        super(FetchAddressIntentService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "address service started");
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        String errorMessage = "";

        // Get the location passed to this service through an extra.
        Location location = intent.getParcelableExtra(
                Constants.LOCATION_DATA_EXTRA);

        List<Address> addresses = null;

        try {
            addresses = geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    // In this sample, get just a single address.
                    1);
        } catch (IOException ioException) {
            // Catch network or other I/O problems.
            errorMessage = "Service not available";
            Log.e(TAG, errorMessage, ioException);
        } catch (IllegalArgumentException illegalArgumentException) {
            // Catch invalid latitude or longitude values.
            errorMessage = "Invalid lat long used";
            Log.e(TAG, errorMessage + ". " +
                    "Latitude = " + location.getLatitude() +
                    ", Longitude = " +
                    location.getLongitude(), illegalArgumentException);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Handle case where no address was found.
        if (addresses == null || addresses.size()  == 0) {
            if (errorMessage.isEmpty()) {
                errorMessage = "No address found";
                Log.e(TAG, errorMessage);
            }
            sharedPreferences.edit().putString(getString(R.string.pref_address_key), "").commit();
        } else {
            Address address = addresses.get(0);
            ArrayList<String> addressFragments = new ArrayList<String>();

            // Fetch the address lines using getAddressLine,
            // join them, and send them to the thread.
            for(int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                addressFragments.add(address.getAddressLine(i));
            }
            Log.i(TAG, "Address Found");
            String addressString = TextUtils.join(System.getProperty("line.separator"), addressFragments);
            Log.d(TAG, addressString);
            sharedPreferences.edit().putString(getString(R.string.pref_address_key), addressString).commit();

            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(getString(R.string.address_intent_filter));
            sendBroadcast(broadcastIntent);
        }

    }
}
