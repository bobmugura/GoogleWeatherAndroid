/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The Weather Company's API for obtaining radar tile images is provided only for the purposes of the hackathon hosted by
 * The Weather Company in June, 2015.
 *
 * The software is provided "as is", without warranty of any kind, express or implied, including but not limited to the
 * warranties of merchantability, fitness for a particular purpose or noninfringement.  In no event shall the authors or
 * copyright holders be liable for any claim, damages or other liability, whether in action of contract, tort or
 * otherwise, arising from, out of or in connection with the software or the use or other dealings in the software.
 */
package com.weather.hackathon.app;

import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;
import com.squareup.okhttp.OkHttpClient;
import com.weather.hackathon.model.LayersFetcher;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import com.weather.hackathon.model.JSONParser;

/**
 * Activity for displaying the map with a weather overlay.
 *
 * @author michael.krussel
 */
public class WeatherMapActivity extends FragmentActivity {
    private static final String LAYER_TO_DISPLAY = "temp";
    private GoogleMap map; // Might be null if Google Play services APK is not available.
    private Handler handler;
    private LayersFetcher layersFetcher;

    JSONParser jsonparser = new JSONParser();
    String ab;
    JSONObject jobj = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_map);
        setUpMapIfNeeded();
        handler = new Handler();
        addMarkers(map);

    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        if (layersFetcher != null) {
            layersFetcher.fetchAsync();
            setupLayerCallback();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void setupLayerCallback() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                layersFetcher.fetchAsync();
                setupLayerCallback();
            }
        }, TimeUnit.MINUTES.toMillis(5));
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #map} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (map == null) {
            // Try to obtain the map from the SupportMapFragment.
            map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (map != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we centered the map
     * on the US and initialized classes for loading the tiles.
     * <p/>
     * This should only be called once and when we are sure that {@link #map} is not null.
     */
    private void setUpMap() {
        map.getUiSettings().setTiltGesturesEnabled(false);
        map.getUiSettings().setRotateGesturesEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(true);

        CameraPosition position = CameraPosition.builder()
                .target(new LatLng(39.8282, -98.5795))
                .zoom(3.5f).build();
        map.moveCamera(CameraUpdateFactory.newCameraPosition(position));

        layersFetcher = new LayersFetcher(new OkHttpClient());
        layersFetcher.setLayersResultListener(new WeatherOverlayCreator(map, LAYER_TO_DISPLAY));
    }

    private void addMarkers(GoogleMap map) {
        Marker user1 = map.addMarker(new MarkerOptions()
                            .position(new LatLng(40, -74))
                            .title("Weather Info")
                            .snippet("Very Hot!"));

        map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                new retrievedata().execute();

//                String weatherInfo = getInfo(marker);
//                Log.i("JSON RESPONSE TEST", weatherInfo);
                return false; //false shows info on click while true does not
            }
        });

    }

    private String getInfo(Marker user) {
        String urlString="http://api.weather.com/v1/geocode/40.0/-74.0/observations/current.json?apiKey=34aae6773a01ce1756979f510dff96b9&language=en-US&units=e";
        String resultToDisplay = "";
        int responseCode = -1;

//        StringBuilder builder = new StringBuilder();
//        HttpClient client = new DefaultHttpClient();
//        HttpGet httpGet = new HttpGet(urlString);
//        try{
//            HttpResponse response = client.execute(httpGet);
//            StatusLine statusLine = response.getStatusLine();
//            int statusCode = statusLine.getStatusCode();
//            if(statusCode == 200){
//                HttpEntity entity = response.getEntity();
//                InputStream content = entity.getContent();
//                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
//                String line;
//                while((line = reader.readLine()) != null){
//                    builder.append(line);
//                }
//            } else {
//                Log.e("JSON STATUS","Failedet JSON object");
//            }
//        }catch(ClientProtocolException e){
//            e.printStackTrace();
//        } catch (IOException e){
//            e.printStackTrace();
//        }
//        return builder.toString();

//        Log.i("JSON RESPONSE TEST", "HERE 0");
//
//        try {
//            URL url = new URL(urlString);
//            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
//            urlConnection.setRequestMethod("GET");
//            urlConnection.setRequestProperty("Accept-Encoding", "gzip");
//            urlConnection.setRequestProperty("Expires", "0");
//            urlConnection.setRequestProperty("Cache-Control", "no-cache");
//            Log.i("JSON RESPONSE TEST", "HERE 1");
//            urlConnection.connect();
//
//            Log.i("JSON RESPONSE TEST", "HERE 2");
//
//            responseCode = urlConnection.getResponseCode();
//            if (responseCode == HttpURLConnection.HTTP_OK) {
//                InputStream inputStream = urlConnection.getInputStream();
//                Reader reader = new InputStreamReader(inputStream);
//                int contentLength = urlConnection.getContentLength();
//                char[] charArray = new char[contentLength];
//                reader.read(charArray);
//                String responseData = new String(charArray);
//                Log.v("JSON RESPONSE!!!!", responseData);
//            } else {
//                Log.i("RESPONSE ERROR", "RESPONSECODE = " + responseCode);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//            return "GetInfo() Passed";

//        RequestQueue requestQueue = Volley.newRequestQueue(getActivity());
//        CustomRequest jsObjRequest = new CustomRequest(Method.POST, url, params, this.createRequestSuccessListener(), this.createRequestErrorListener());
//
//        requestQueue.add(jsObjRequest);

        Log.i("JSON RESPONSE TEST", "HERE 0");
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet("http://api.weather.com/v1/geocode/40.0/-74.0/observations/current.json?apiKey=34aae6773a01ce1756979f510dff96b9&language=en-US&units=m");
        Log.i("JSON RESPONSE TEST", "HERE 00");
        try {
            Log.i("JSON RESPONSE TEST", "HERE 1");

            HttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();

            Log.i("JSON RESPONSE TEST", "HERE 11");

            if (entity != null) {
                InputStream inputstream = entity.getContent();
//                BufferedReader bufferedreader =
//                        new BufferedReader(new InputStreamReader(inputstream));
                Reader reader = new InputStreamReader(inputstream);
                Log.i("JSON RESPONSE TEST", "HERE 2");
                int contentLength = (int) entity.getContentLength();
                Log.i("JSON RESPONSE TEST", "HERE 3");
                char[] charArray = new char[contentLength];
                reader.read(charArray);
                String responseData = new String(charArray);
                Log.v("JSON RESPONSE!!!!", responseData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Info not available";
    }

    class retrievedata extends AsyncTask<String,String,String> {

        JSONObject jobj = null;

        @Override
        protected String doInBackground(String... arg0) {
            // TODO Auto-generated method stub
            jobj = jsonparser.makeHttpRequest("http://api.weather.com/v1/geocode/40.0/-74.0/observations/current.json?apiKey=34aae6773a01ce1756979f510dff96b9&language=en-US&units=e");

            // check your log for json response
            Log.d("Login attempt", jobj.toString());

            try {
                ab = jobj.getString("observation");
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return ab;
        }
        protected void onPostExecute(String ab){

            try {
                JSONObject jsonObject = new JSONObject(ab);
                //JSONArray jsonArray = new JSONArray(jsonObject);
                Log.i("Returned JSONObject", jsonObject.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            //JSONArray jsonArray = new JSONArray(ab);

        }

    }
}
/* with apache
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet("http://api.weather.com/v1/geocode/40/-74/observations/current.json?apiKey={34aae6773a01ce1756979f510dff96b9}&language=en-US&units=m");

        try {
            HttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                InputStream inputstream = entity.getContent();
                BufferedReader bufferedreader =
                        new BufferedReader(new InputStreamReader(inputstream));
                StringBuilder stringbuilder = new StringBuilder();

                String currentline = null;
                while ((currentline = bufferedreader.readLine()) != null) {
                    stringbuilder.append(currentline + "\n");
                }
                String result = stringbuilder.toString();
                Log.v("HTTP REQUEST", result);
                inputstream.close();
                return result;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Info not available";
 */