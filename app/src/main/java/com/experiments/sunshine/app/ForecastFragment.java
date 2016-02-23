
package com.experiments.sunshine.app;

/**
 * Created on 2/2/16.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
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
    private String locationSetting;
    private FetchAddressReceiver fetchAddressReceiver;
    private boolean receiverRegistered;

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

        locationSetting = Utility.getPreferredLocation(getActivity());

        if (!TextUtils.isEmpty(locationSetting)) {
            getLoaderManager().initLoader(WEATHER_LOADER_ID, null, this);
        }

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
    public void onPause() {
        super.onPause();

        if (fetchAddressReceiver != null && receiverRegistered) {
                getActivity().unregisterReceiver(fetchAddressReceiver);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_view_location) {
            if (forecastAdapter != null) {
                Cursor cursor = forecastAdapter.getCursor();
                if (cursor != null) {
                    cursor.moveToFirst();
                    String posLat = Float.toString(cursor.getFloat(COL_COORD_LAT));
                    String posLong = Float.toString(cursor.getFloat(COL_COORD_LONG));
                    Uri geoUri = Uri.parse("geo:" + posLat + "," + posLong);

                    Log.d(LOG_TAG, "MAPS: " + geoUri);
                    Log.d(LOG_TAG, "cursor size " + cursor.getCount());

                    Intent intent = new Intent(Intent.ACTION_VIEW);

                    Uri locationUri = geoUri
                            .buildUpon()
                            .appendQueryParameter("q", posLat + "," + posLong)
                            .build();
                    intent.setData(locationUri);

                    if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                        startActivity(intent);
                    }
                    else {
                        Log.d(LOG_TAG, "Couldn't display location. No receiving apps installed.");
                        Toast.makeText(getActivity(), "Maps not installed", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateWeather() {
        SunshineSyncAdapter.syncImmediately(getActivity());
    }

    protected void onLocationChanged() {
        updateWeather();
        locationSetting = Utility.getPreferredLocation(getActivity());
        getLoaderManager().restartLoader(WEATHER_LOADER_ID, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case WEATHER_LOADER_ID:
                Log.d(LOG_TAG, "on create loader");
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
                Log.d(LOG_TAG, "on load finished");
                forecastAdapter.swapCursor(data);

                if (mPosition != ListView.INVALID_POSITION) {
                    forecastListView.setItemChecked(mPosition, true);
                    forecastListView.smoothScrollToPosition(mPosition);

                    forecastListView.performItemClick(forecastAdapter.getView(mPosition, null, null),
                            mPosition,
                            forecastAdapter.getItemId(mPosition));
                }
                else if (((MainActivity) getActivity()).isTwoPane()) {
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
        Log.d(LOG_TAG, "on loader reset");
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

    public void initializeLoader() {
        if (getLoaderManager().getLoader(ForecastFragment.WEATHER_LOADER_ID) == null) {
            getLoaderManager().initLoader(ForecastFragment.WEATHER_LOADER_ID, null, this);
        }
    }

    public class FetchAddressReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(LOG_TAG, "received address " + intent.getAction());
            locationSetting = Utility.getPreferredLocation(getActivity());
            SunshineSyncAdapter.initializeSyncAdapter(getActivity());
            getLoaderManager().initLoader(WEATHER_LOADER_ID, null, ForecastFragment.this);
        }
    }
}
