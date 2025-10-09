package com.example.g_trackapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.maps.android.PolyUtil;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CollectorRouteSuggestionActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private final String GOOGLE_DIRECTIONS_API_KEY = BuildConfig.MAPS_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collector_route_suggestion);

        // --- DEBUG: log API key ---
        Log.d("RouteSuggestion", "Using API key: " + GOOGLE_DIRECTIONS_API_KEY);

        // --- Map fragment ---
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.routeMap);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // --- Top-right menu ---
        ImageView btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu != null) btnMenu.setOnClickListener(v -> showPopupMenu(btnMenu));

        // --- Bottom navigation buttons ---
        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        ImageView btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, CollectorMainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        ImageView btnAlarm = findViewById(R.id.btnAlarm);
        if (btnAlarm != null) btnAlarm.setOnClickListener(v ->
                Toast.makeText(this, "Alarm clicked", Toast.LENGTH_SHORT).show()
        );
    }

    private void showPopupMenu(ImageView anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_top_right, popup.getMenu());
        popup.setOnMenuItemClickListener(this::handleMenuClick);
        popup.show();
    }

    private boolean handleMenuClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, CollectorAccountSettingActivity.class));
            return true;
        } else if (id == R.id.action_contact) {
            Toast.makeText(this, "Contact Us clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_about) {
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            getSharedPreferences("ResidentPrefs", MODE_PRIVATE).edit().clear().apply();
            Intent intent = new Intent(this, ResidentLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        // --- Hardcoded points A -> B -> C -> D ---
        LatLng pointA = new LatLng(14.5995, 120.9842); // Manila
        LatLng pointB = new LatLng(14.6045, 120.9882);
        LatLng pointC = new LatLng(14.6075, 120.9922);
        LatLng pointD = new LatLng(14.6095, 120.9942);

        // Add markers
        mMap.addMarker(new MarkerOptions().position(pointA).title("Point A"));
        mMap.addMarker(new MarkerOptions().position(pointB).title("Point B"));
        mMap.addMarker(new MarkerOptions().position(pointC).title("Point C"));
        mMap.addMarker(new MarkerOptions().position(pointD).title("Point D"));

        // Move camera to first point
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pointA, 14));

        // Fetch optimized route using Google Directions API
        fetchRoute(pointA, pointD, new LatLng[]{pointB, pointC});
    }

    private void fetchRoute(LatLng origin, LatLng destination, LatLng[] waypoints) {
        try {
            StringBuilder waypointsStr = new StringBuilder("optimize:true");
            for (LatLng point : waypoints) {
                waypointsStr.append("|").append(point.latitude).append(",").append(point.longitude);
            }

            String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=" + origin.latitude + "," + origin.longitude +
                    "&destination=" + destination.latitude + "," + destination.longitude +
                    "&waypoints=" + waypointsStr +
                    "&mode=driving" +
                    "&key=" + GOOGLE_DIRECTIONS_API_KEY;

            Log.d("RouteSuggestion", "Directions URL: " + url);

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() ->
                            Toast.makeText(CollectorRouteSuggestionActivity.this, "Failed to fetch route", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.body() != null) {
                        String json = response.body().string();
                        Log.d("DirectionsJSON", json);

                        if (json.contains("REQUEST_DENIED")) {
                            Log.e("RouteSuggestion", "API key invalid or restricted");
                            runOnUiThread(() ->
                                    Toast.makeText(CollectorRouteSuggestionActivity.this, "Invalid API key", Toast.LENGTH_LONG).show());
                        } else {
                            parseAndDrawRoute(json);
                        }
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseAndDrawRoute(String json) {
        Gson gson = new Gson();
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        JsonArray routes = obj.getAsJsonArray("routes");
        if (routes.size() == 0) {
            Log.d("RouteSuggestion", "No routes found in response");
            return;
        }

        JsonObject route = routes.get(0).getAsJsonObject();
        String encodedPolyline = route.getAsJsonObject("overview_polyline").get("points").getAsString();
        List<LatLng> path = PolyUtil.decode(encodedPolyline);

        runOnUiThread(() -> {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(path)
                    .color(0xFF2196F3)
                    .width(8);
            mMap.addPolyline(polylineOptions);

            // Zoom to show entire route
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (LatLng point : path) builder.include(point);
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
        });
    }
}
