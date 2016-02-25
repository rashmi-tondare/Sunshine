/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.experiments.sunshine.app;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.experiments.sunshine.app.data.WeatherContract;
import com.experiments.sunshine.app.services.FetchAddressIntentService;
import com.experiments.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

/**
 * A {@link PreferenceActivity} that presents a set of application settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int PLACE_PICKER_REQUEST = 1;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add 'general' preferences, defined in the XML file
        addPreferencesFromResource(R.xml.pref_general);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // For all preferences, attach an OnPreferenceChangeListener so the UI summary can be
        // updated when the preference changes.
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_location_key)));
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_units_key)));
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_enable_notifications_key)));
    }

    @Override
    protected void onResume() {
        super.onResume();

        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Attaches a listener so the summary is always updated with the preference value.
     * Also fires the listener once, to initialize the summary (so it shows up before the value
     * is changed.)
     */
    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(this);

        // Trigger the listener immediately with the preference's
        // current value.
        if (preference instanceof CheckBoxPreference) {
            onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getBoolean(preference.getKey(), true));
        }
        else {
            onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        String stringValue = value.toString();

        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list (since they have separate labels/values).
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(stringValue);
            if (prefIndex >= 0) {
                preference.setSummary(listPreference.getEntries()[prefIndex]);
            }
        }
        else {
            // For other preferences, set the summary to the value's simple string representation.
            preference.setSummary(stringValue);
            if (preference.getKey().equals(getString(R.string.pref_location_key))) {
                @SunshineSyncAdapter.LocationStatus int status = Utility.getLocationStatus(this);
                switch (status) {
                    case SunshineSyncAdapter.LOCATION_STATUS_OK:
                        preference.setSummary(stringValue);
                        break;
                    case SunshineSyncAdapter.LOCATION_STATUS_UNKNOWN:
                        preference.setSummary(getString(R.string.pref_location_unknown_description));
                        break;
                    case SunshineSyncAdapter.LOCATION_STATUS_INVALID:
                        preference.setSummary(getString(R.string.empty_forecast_list_invalid_location));
                        break;
                    default:
                        preference.setSummary(stringValue);
                        break;
                }
                preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);

                        double lat = sharedPreferences.getFloat(getString(R.string.pref_latitude_key), Float.parseFloat(getString(R.string.latitude_default)));
                        double lon = sharedPreferences.getFloat(getString(R.string.pref_longitude_key), Float.parseFloat(getString(R.string.longitude_default)));
                        LatLngBounds latLngBounds = new LatLngBounds(new LatLng(lat, lon), new LatLng(lat, lon));
                        builder.setLatLngBounds(latLngBounds);

                        try {
                            startActivityForResult(builder.build(SettingsActivity.this), PLACE_PICKER_REQUEST);
                        } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
                            e.printStackTrace();
                        }
                        return true;
                    }
                });
            }
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public Intent getParentActivityIntent() {
        return super.getParentActivityIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PLACE_PICKER_REQUEST:
                if (resultCode == RESULT_OK) {
                    Place place = PlacePicker.getPlace(this, data);
                    CharSequence address = place.getAddress();
                    float lat = (float) place.getLatLng().latitude;
                    float lon = (float) place.getLatLng().longitude;

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    if (!TextUtils.isEmpty(address)) {
                        editor.putString(getString(R.string.pref_location_key), address.toString());
                    }
                    else {
                        Location location = new Location(MainActivity.class.getSimpleName());
                        location.setLatitude(lat);
                        location.setLongitude(lon);
                        Intent intent = new Intent(this, FetchAddressIntentService.class);
                        intent.putExtra(Constants.LOCATION_DATA_EXTRA, location);
                        startService(intent);
                    }
                    editor.putFloat(getString(R.string.pref_latitude_key), lat);
                    editor.putFloat(getString(R.string.pref_longitude_key), lon);
                    editor.commit();

                    onPreferenceChange(findPreference(getString(R.string.pref_location_key)), address.toString());
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_location_key))) {
            Utility.resetCurrentLocationStatus(this);
            onPreferenceChange(findPreference(getString(R.string.pref_location_key)), sharedPreferences.getString(key, getString(R.string.pref_location_default)));
        }
        else if (key.equals(getString(R.string.pref_units_key))) {
            getContentResolver().notifyChange(WeatherContract.WeatherEntry.CONTENT_URI, null);
        }
    }
}