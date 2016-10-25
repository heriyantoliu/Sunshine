package com.example.android.sunshine;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.format.Time;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.data.WeatherContract.WeatherEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Vector;

/**
 * Created by Heriyanto on 10/3/2016.
 */

public class FetchWeatherTask extends AsyncTask<String, Void, Void> {

    private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();
    //private ArrayAdapter<String> mForecastAdapter;
    private final Context mContext;

    public FetchWeatherTask(Context context){
        mContext = context;
    }

    private boolean DEBUG = true;

    @Override
    protected Void doInBackground(String... params) {

        if ( params == null){
            return null;
        }
        String locationQuery = params[0];

        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        String forecastJsonStr = null;
        String format = "json";
        String units = "metric";
        int numDays = 14;

        try {

            String apiKey = "&APPID=" + BuildConfig.OPEN_WEATHER_MAP_API_KEY;
            final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
            final String QUERY_PARAM = "zip";
            final String FORMAT_PARAM = "mode";
            final String UNITS_PARAM = "units";
            final String DAYS_PARAM = "cnt";
            final String APPID_PARAM = "APPID";

            Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, params[0])
                    .appendQueryParameter(FORMAT_PARAM, format)
                    .appendQueryParameter(UNITS_PARAM, units)
                    .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                    .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                    .build();

            URL url = new URL(builtUri.toString());
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            //Read the input stream into String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                return null;
            }

            forecastJsonStr = buffer.toString();
            getWeatherDataFromJson(forecastJsonStr, locationQuery);

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error", e);
        } catch (JSONException e){
            Log.e(LOG_TAG, e.getMessage(),e);
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }

        return null;
    }


    private void getWeatherDataFromJson (String forecastJsonStr, String locationSetting)
            throws JSONException{

        final String OWN_CITY = "city";
        final String OWN_CITY_NAME = "name";
        final String OWN_COORD = "coord";

        final String OWN_LATITUDE = "lat";
        final String OWN_LONGITUDE = "lon";

        final String OWN_LIST = "list";

        final String OWN_PRESSURE = "pressure";
        final String OWN_HUMIDITY = "humidity";
        final String OWN_WINSPEED = "speed";
        final String OWN_WIND_DIRECTION = "deg";

        final String OWN_TEMPERATURE = "temp";
        final String OWN_MAX = "max";
        final String OWN_MIN = "min";

        final String OWN_WEATHER = "weather";
        final String OWN_DESCRIPTION = "main";
        final String OWN_WEATHER_ID = "id";
        try{
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWN_LIST);

            JSONObject cityJson = forecastJson.getJSONObject(OWN_CITY);
            String cityName = cityJson.getString(OWN_CITY_NAME);

            JSONObject cityCoord = cityJson.getJSONObject(OWN_COORD);
            double cityLatitude = cityCoord.getDouble(OWN_LATITUDE);
            double cityLongitude = cityCoord.getDouble(OWN_LONGITUDE);

            long locationId = addLocation(locationSetting, cityName,cityLatitude, cityLongitude);

            Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());

            Time dayTime = new Time();
            dayTime.setToNow();

            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            dayTime = new Time();

            for( int i = 0; i<weatherArray.length();i++){
                long dateTime;
                double pressure;
                int humidity;
                double windSpeed;
                double windDirection;

                double high;
                double low;

                String description;
                int weatherId;

                JSONObject dayForecast = weatherArray.getJSONObject(i);

                dateTime = dayTime.setJulianDay(julianStartDay+i);
                pressure = dayForecast.getDouble(OWN_PRESSURE);
                humidity = dayForecast.getInt(OWN_HUMIDITY);
                windSpeed = dayForecast.getDouble(OWN_WINSPEED);
                windDirection = dayForecast.getDouble(OWN_WIND_DIRECTION);

                JSONObject weatherObject = dayForecast.getJSONArray(OWN_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWN_DESCRIPTION);
                weatherId = weatherObject.getInt(OWN_WEATHER_ID);

                JSONObject temperatureObject = dayForecast.getJSONObject(OWN_TEMPERATURE);
                high = temperatureObject.getDouble(OWN_MAX);
                low = temperatureObject.getDouble(OWN_MIN);

                ContentValues weatherValues = new ContentValues();

                weatherValues.put(WeatherEntry.COLUMN_LOC_KEY, locationId);
                weatherValues.put(WeatherEntry.COLUMN_DATE, dateTime);
                weatherValues.put(WeatherEntry.COLUMN_HUMIDITY, humidity);
                weatherValues.put(WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherEntry.COLUMN_DEGREES,windDirection);
                weatherValues.put(WeatherEntry.COLUMN_MAX_TEMP, high);
                weatherValues.put(WeatherEntry.COLUMN_MIN_TEMP, low);
                weatherValues.put(WeatherEntry.COLUMN_SHORT_DESC, description);
                weatherValues.put(WeatherEntry.COLUMN_WEATHER_ID, weatherId);

                cVVector.add(weatherValues);

            }

            int inserted = 0;

            if ( cVVector.size() > 0){
                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                inserted = mContext.getContentResolver().bulkInsert(WeatherEntry.CONTENT_URI, cvArray);
            }

            Log.d(LOG_TAG, "FetchWeatherTask Complete. " + inserted + " Inserted");

        } catch (JSONException e){
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }
        //return null;
    }

    public long addLocation(String locationSetting, String cityName, double lat, double lon){
        long locationId;

        Cursor locationCursor = mContext.getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry._ID},
                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                new String[]{locationSetting},
                null
        );

        if (locationCursor.moveToFirst()){
            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry._ID);
            locationId = locationCursor.getLong(locationIdIndex);
        } else {
            ContentValues locationValues = new ContentValues();
            locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, cityName);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LAT, lat);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LONG, lon);

            Uri insertedUri = mContext.getContentResolver().insert(
                    WeatherContract.LocationEntry.CONTENT_URI,
                    locationValues
            );

            locationId = ContentUris.parseId(insertedUri);
        }

        locationCursor.close();

        return locationId;
    }

}
