package com.experiments.sunshine.app;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.experiments.sunshine.app.services.FetchAddressIntentService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity implements ForecastFragment.Callback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    protected static final String DETAILFRAGMENT_TAG = "DFTAG";
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private String mLocation;
    private boolean mTwoPane;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private SharedPreferences sharedPreferences;
    private boolean receiverRegistered;
    private ForecastFragment.FetchAddressReceiver fetchAddressReceiver;

    protected void startIntentService() {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastLocation);
        startService(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (findViewById(R.id.weather_detail_container) != null) {
            mTwoPane = true;

            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.weather_detail_container, new DetailFragment(), DETAILFRAGMENT_TAG)
                        .commit();
            }
        } else {
            mTwoPane = false;
            getSupportActionBar().setElevation(0f);
        }

        ForecastFragment forecastFragment = (ForecastFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_forecast);
        forecastFragment.setUseTodayLayout(!mTwoPane);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mLocation = sharedPreferences.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));

        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                    if (mLastLocation != null) {
                        double latitude = mLastLocation.getLatitude();
                        double longitude = mLastLocation.getLongitude();

                        sharedPreferences.edit().putFloat(getString(R.string.pref_latitude_key), (float) latitude).commit();
                        sharedPreferences.edit().putFloat(getString(R.string.pref_longitude_key), (float) longitude).commit();

                    }
                    else {
                        mLastLocation = new Location(MainActivity.class.getSimpleName());
                        mLastLocation.setLatitude(Double.parseDouble(getString(R.string.latitude_default)));
                        mLastLocation.setLongitude(Double.parseDouble(getString(R.string.longitude_default)));
                    }
                    startIntentService();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.e(TAG, "Permission denied");
                }
                break;
            default:
                Log.e(TAG, "Invalid request code!");

        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "Main activity resume");
        String storedLocation = Utility.getPreferredLocation(getApplicationContext());
        ForecastFragment forecastFragment = (ForecastFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_forecast);
        receiverRegistered = false;

        if (TextUtils.isEmpty(storedLocation)) {
            if (fetchAddressReceiver == null) {
                fetchAddressReceiver = forecastFragment.new FetchAddressReceiver();
            }
            Log.d(TAG, "registered receiver");
            receiverRegistered = true;
            IntentFilter intentFilter = new IntentFilter(getString(R.string.address_intent_filter));
            registerReceiver(fetchAddressReceiver, intentFilter);
        }
        else {
            forecastFragment.initializeLoader();
        }

        if (storedLocation != null && !storedLocation.equals(mLocation)) {
            if (forecastFragment != null) {
                forecastFragment.onLocationChanged();
            }

            DetailFragment detailFragment = (DetailFragment) getSupportFragmentManager().findFragmentByTag(DETAILFRAGMENT_TAG);
            if (detailFragment != null) {
                detailFragment.onLocationChanged(storedLocation);
            }
            mLocation = storedLocation;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (fetchAddressReceiver != null && receiverRegistered) {
            unregisterReceiver(fetchAddressReceiver);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(Uri contentUri) {
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            Bundle args = new Bundle();
            args.putParcelable(DetailFragment.DETAIL_URI, contentUri);

            DetailFragment fragment = new DetailFragment();
            fragment.setArguments(args);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.weather_detail_container, fragment, DETAILFRAGMENT_TAG)
                    .commitAllowingStateLoss();
        } else {
            Intent intent = new Intent(this, DetailActivity.class)
                    .setData(contentUri);
            startActivity(intent);
        }
    }

    public boolean isTwoPane() {
        return mTwoPane;
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        }

        String address = sharedPreferences.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        if (address.equals(getString(R.string.pref_location_default))) {
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (mLastLocation != null) {
                double latitude = mLastLocation.getLatitude();
                double longitude = mLastLocation.getLongitude();

                sharedPreferences.edit().putFloat(getString(R.string.pref_latitude_key), (float) latitude).commit();
                sharedPreferences.edit().putFloat(getString(R.string.pref_longitude_key), (float) longitude).commit();
            }
            else {
                mLastLocation = new Location(MainActivity.class.getSimpleName());
                mLastLocation.setLatitude(Double.parseDouble(getString(R.string.latitude_default)));
                mLastLocation.setLongitude(Double.parseDouble(getString(R.string.longitude_default)));
            }
            startIntentService();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}