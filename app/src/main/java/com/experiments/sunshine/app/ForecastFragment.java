
package com.experiments.sunshine.app;

/**
 * Created on 2/2/16.
 */

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.experiments.sunshine.app.data.WeatherContract;
import com.experiments.sunshine.app.data.WeatherContract.LocationEntry;
import com.experiments.sunshine.app.data.WeatherContract.WeatherEntry;
import com.experiments.sunshine.app.sync.SunshineSyncAdapter;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int MY_PERMISSIONS_REQUEST_INTERNET = 1;
    private static final String LOG_TAG = ForecastFragment.class.getSimpleName();

    private static final int WEATHER_LOADER_ID = 1;
    protected static final String[] FORECAST_COLUMNS = {
                                                         WeatherEntry.TABLE_NAME + "."
                                                         + WeatherEntry._ID,
                                                         WeatherEntry.COLUMN_DATE,
                                                         WeatherEntry.COLUMN_SHORT_DESC,
                                                         WeatherEntry.COLUMN_MAX_TEMP,
                                                         WeatherEntry.COLUMN_MIN_TEMP,
                                                         LocationEntry.COLUMN_LOCATION_SETTING,
                                                         WeatherEntry.COLUMN_WEATHER_ID,
                                                         LocationEntry.COLUMN_COORD_LAT,
                                                         LocationEntry.COLUMN_COORD_LONG };

    // These indices are tied to FORECAST_COLUMNS.  If FORECAST_COLUMNS changes, these
    // must change.
    protected static final int COL_WEATHER_ID = 0;
    protected static final int COL_WEATHER_DATE = 1;
    protected static final int COL_WEATHER_DESC = 2;
    protected static final int COL_WEATHER_MAX_TEMP = 3;
    protected static final int COL_WEATHER_MIN_TEMP = 4;
    protected static final int COL_LOCATION_SETTING = 5;
    protected static final int COL_WEATHER_CONDITION_ID = 6;
    protected static final int COL_COORD_LAT = 7;
    protected static final int COL_COORD_LONG = 8;
    private static final String BUNDLE_POSITION = "bundle_position";

    private ForecastAdapter forecastAdapter;
    private ListView forecastListView;
    private int mPosition = ListView.INVALID_POSITION;
    private boolean mUseTodayLayout;

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callback {
        /**
         * DetailFragmentCallback for when an item has been selected.
         */
        public void onItemSelected(Uri dateUri);
    }

    public ForecastFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        setHasOptionsMenu(true);
        forecastListView = (ListView) rootView.findViewById(R.id.listview_forecast);
        forecastAdapter = new ForecastAdapter(getActivity(), null, 0);
        forecastListView.setAdapter(forecastAdapter);

        forecastListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // CursorAdapter returns a cursor at the correct position for getItem(), or null
                // if it cannot seek to that position.
                Cursor cursor = (Cursor) parent.getItemAtPosition(position);
                if (cursor != null) {
                    String locationSetting = Utility.getPreferredLocation(getActivity());
                    ((Callback) getActivity())
                            .onItemSelected(WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                                    locationSetting, cursor.getLong(COL_WEATHER_DATE)));
                }
                mPosition = position;
            }
        });
        forecastAdapter.setUseTodayLayout(mUseTodayLayout);

        getLoaderManager().initLoader(WEATHER_LOADER_ID, null, this);

        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_POSITION)) {
            mPosition = savedInstanceState.getInt(BUNDLE_POSITION);
        }

        return rootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mPosition != ListView.INVALID_POSITION) {
            outState.putInt(BUNDLE_POSITION, mPosition);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        getActivity().getMenuInflater().inflate(R.menu.forecast_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            if (ContextCompat.checkSelfPermission(getActivity().getApplicationContext(), Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
                updateWeather();
            }
            else {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[] { Manifest.permission.INTERNET },
                        MY_PERMISSIONS_REQUEST_INTERNET);
            }
            return true;
        }
        else if (id == R.id.action_view_location) {
            Intent intent = new Intent(Intent.ACTION_VIEW);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
            String location = sharedPreferences.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));

            Uri locationUri = Uri.parse("geo:0,0")
                    .buildUpon()
                    .appendQueryParameter("q", location)
                    .build();
            intent.setData(locationUri);

            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivity(intent);
            }
            else {
                Log.d(LOG_TAG, "Couldn't diplay " + location + ". No receiving apps installed.");
                Toast.makeText(getActivity(), "Maps not installed", Toast.LENGTH_SHORT).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateWeather() {
        SunshineSyncAdapter.syncImmediately(getActivity());
    }

    protected void onLocationChanged() {
        updateWeather();
        getLoaderManager().restartLoader(WEATHER_LOADER_ID, null, this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_INTERNET: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // task you need to do.
                    updateWeather();

                }
                else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(getActivity(), "Internet permission denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }

                // other 'case' lines to check for other
                // permissions this app might request
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case WEATHER_LOADER_ID:
                String locationSetting = Utility.getPreferredLocation(getActivity());

                // Sort order:  Ascending, by date.
                String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
                Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                        locationSetting, System.currentTimeMillis());

                return new CursorLoader(getActivity().getApplicationContext(),
                        weatherForLocationUri,
                        FORECAST_COLUMNS,
                        null,
                        null,
                        sortOrder);
            default:
                return null;

        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        switch (loader.getId()) {
            case WEATHER_LOADER_ID:
                forecastAdapter.swapCursor(data);

                if (mPosition != ListView.INVALID_POSITION) {
                    forecastListView.setItemChecked(mPosition, true);
                    forecastListView.smoothScrollToPosition(mPosition);

                    forecastListView.performItemClick(forecastAdapter.getView(mPosition, null, null),
                            mPosition,
                            forecastAdapter.getItemId(mPosition));
                }
                else if (((MainActivity) getActivity()).isTwoPane()){
                    forecastListView.setItemChecked(0, true);
                    if (data.moveToFirst()) {
                        forecastListView.performItemClick(forecastAdapter.getView(0, null, null),
                                0,
                                forecastAdapter.getItemId(0));
                    }

                }

                break;
            default:
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch (loader.getId()) {
            case WEATHER_LOADER_ID:
                forecastAdapter.swapCursor(null);
                break;
            default:
                break;
        }
    }

    public void setUseTodayLayout(boolean useTodayLayout) {
        this.mUseTodayLayout = useTodayLayout;
        if (forecastAdapter != null) {
            forecastAdapter.setUseTodayLayout(mUseTodayLayout);
        }
    }
}
