/**
 * This app takes a location and alerts you if you are within 200m of that place.
 * It uses the Fused Location Api to get your current location, Places Api for selecting a point,
 * and SharedPreferences for storing the last requested place.
 *
 * @version 4/27/16
 * @authors Nasif Sikder <nasif1@umbc.edu>, Rob Grossman <rgross1@umbc.edu>
 * @assignment CMSC 491 - Spring 2016 - Assignment 2

 */



package com.example.rsg.locationalert;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private Button find_;
    private GoogleMap mMap;
    private static String preference = "PREFS";
    protected GoogleApiClient mGoogleApiClient;
    protected LocationRequest mLocationRequest;
    protected Location mCurrentLocation;
    protected LatLng lastLoc;
    protected TextView mLatLngText;
    protected SharedPreferences lastLocation;
    protected int PLACE_PICKER_REQUEST = 1;
    private Marker marker;
    private Place place;
    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        find_ = (Button) findViewById(R.id.buttonFind);

        find_.setOnClickListener(this);

        mLatLngText = (TextView) findViewById(R.id.textLatLng);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        lastLocation = getSharedPreferences(preference, Context.MODE_PRIVATE);


        buildGoogleApiClient();

    }


    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .enableAutoManage(this, this)
                .build();
        createLocationRequest();
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);

            return;
        }

        Log.d("debug", "connected");

        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        double lat;
        double lng;

        if (mCurrentLocation != null) {
            lat = mCurrentLocation.getLatitude();
            lng = mCurrentLocation.getLongitude();

            mLatLngText.setText("Lat: " + lat + "\nLng: " + lng);
        }

        startLocationUpdates();

    }



    /**
     * Stores the last requested place in SharedPreferences
     */
    @Override
    protected void onPause() {
        super.onPause();
        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }

        if (place != null) {
            SharedPreferences.Editor edit = lastLocation.edit();
            edit.putLong("Lat", Double.doubleToRawLongBits(place.getLatLng().latitude));
            edit.putLong("Lng", Double.doubleToRawLongBits(place.getLatLng().longitude));
            edit.commit();
        }

    }


    /**
     * Retrieves that last requested place from SharedPreferences
     */
    @Override
    public void onResume() {
        super.onResume();

        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }

        double lat = Double.longBitsToDouble(lastLocation.getLong("Lat", 0));
        double lng = Double.longBitsToDouble(lastLocation.getLong("Lng", 0));

        if (lat != 0 && lng != 0){
            lastLoc = new LatLng(lat, lng);

            if (mMap != null) {
                mMap.addMarker(new MarkerOptions().position(lastLoc));
                mMap.moveCamera(CameraUpdateFactory.newLatLng(lastLoc));
            }
        }

        //Log.d("debug", "Lat: " + lat + " Long: " + lng);

    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d("Connection failed: ", connectionResult.toString());
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
//         It is a good practice to remove location requests when the activity is in a paused or
//         stopped state. Doing so helps battery performance and is especially
//         recommended in applications that request frequent location updates.
//
//         The final argument to {@code requestLocationUpdates()} is a LocationListener
//         (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case (R.id.buttonFind):
                PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();

                try {
                    startActivityForResult(builder.build(this), PLACE_PICKER_REQUEST);
                } catch (GooglePlayServicesRepairableException e) {

                    Log.d("Place picker: " , e.toString());
                } catch (GooglePlayServicesNotAvailableException e) {
                    Log.d("Place picker: " , e.toString());
                }
                break;
        }
    }

    /**
     * Callback that's invoked when a location is selected from the Places activity.
     * It sets the new place and adds a marker for the new location and zooms to it
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER_REQUEST) {
            if (resultCode == RESULT_OK) {
                //remove the previous marker before adding the new place
                    if(marker != null) {
                        marker.remove();
                    }
                //set the new place
                place = PlacePicker.getPlace(this, data);
                String toastMsg = String.format("Place: %s", place.getName());
                Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
                //add a marker at the location of the new place
                marker = mMap.addMarker(new MarkerOptions().position(place.getLatLng()));
            }
        }
    }


    /**
     * Callback that fires when the Map is rendered. It adds a marker for the last place that you requested
     * and zooms to it
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (lastLoc != null) {
            //add a marker to the current position
            mMap.addMarker(new MarkerOptions().position(lastLoc));

            //zoom in and reposition the camera to the marker at the same time
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastLoc, 12.0f));
        }

    }


    /**
     * Requests location updates from the FusedLocationApi.
     */
    protected void startLocationUpdates() {
        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);

    }

    /**
     * Callback that fires when the location changes. The new location's distance is compared with the
     * requested location. If the distance is <= 200 meters, the user will be notified.
     */
    @Override
    public void onLocationChanged(Location location) {
        //Log.d("debug", "location changed");
        mCurrentLocation = location;
        if(place != null) {
            LatLng placeLoc = place.getLatLng();
            float[] results = new float[1];

            //calculate the distance and store it in results
            Location.distanceBetween(placeLoc.latitude, placeLoc.longitude,
                    location.getLatitude(), location.getLongitude(), results);

            //make a toast whenever the distance is <= 200
            int distance = (int) results[0];
            if (distance <= 200) {
                String toastMsg = String.format("Within 200meters of %s", place.getName());
                Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
            }
        }
    }
}
