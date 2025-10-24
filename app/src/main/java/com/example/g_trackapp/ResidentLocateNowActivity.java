package com.example.g_trackapp;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class ResidentLocateNowActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private ResidentSessionManager sessionManager;
    private FirebaseFirestore db;
    private final Map<String, Marker> collectorMarkers = new HashMap<>();
    private final Map<String, LatLng> lastKnownPositions = new HashMap<>();
    private ListenerRegistration collectorListener;
    private boolean firstCollectorShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_locate_now);

        sessionManager = new ResidentSessionManager(this);
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // ✅ Check and request location permissions
        requestLocationPermission();

        // ✅ Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // ✅ UI buttons
        findViewById(R.id.btnYourLocation).setOnClickListener(v -> getDeviceLocation());
        findViewById(R.id.btnDropOffLocation).setOnClickListener(v ->
                Toast.makeText(this, "Drop-off location selected", Toast.LENGTH_SHORT).show()
        );

        // ✅ Menu and navigation buttons
        findViewById(R.id.btnMenu).setOnClickListener(v -> showPopupMenu((ImageView) v));

        ImageView btnBackBottom = findViewById(R.id.btnBack);
        if (btnBackBottom != null) btnBackBottom.setOnClickListener(v -> finish());

        ImageView btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, ResidentMainMenuActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        ImageView btnAlarm = findViewById(R.id.btnAlarm);
        if (btnAlarm != null) {
            btnAlarm.setOnClickListener(v ->
                    Toast.makeText(this, "Alarm clicked", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }

        // ✅ For Android 13+ push notification permission (optional)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            getDeviceLocation();
            listenToAllCollectors();
        } else {
            requestLocationPermission();
        }
    }

    private void getDeviceLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null && mMap != null) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                        } else {
                            Toast.makeText(this, "Unable to detect your location", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    /** ✅ Listen to all collectors in real-time */
    private void listenToAllCollectors() {
        collectorListener = db.collection("collectors").addSnapshotListener((querySnapshot, e) -> {
            if (e != null || querySnapshot == null) return;

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {  // ✅ Fixed
                String id = doc.getId();
                Double lat = doc.getDouble("latitude");
                Double lon = doc.getDouble("longitude");
                String name = doc.getString("firstName");

                if (lat == null || lon == null) continue;

                LatLng newPos = new LatLng(lat, lon);

                // ✅ Skip if collector hasn’t moved
                if (lastKnownPositions.containsKey(id)) {
                    LatLng lastPos = lastKnownPositions.get(id);
                    if (lastPos != null && lastPos.equals(newPos)) continue;
                }
                lastKnownPositions.put(id, newPos);

                Marker existingMarker = collectorMarkers.get(id);

                if (existingMarker == null) {
                    MarkerOptions options = new MarkerOptions()
                            .position(newPos)
                            .title("Collector: " + (name != null ? name : id))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));

                    Marker newMarker = mMap.addMarker(options);
                    collectorMarkers.put(id, newMarker);

                    // ✅ Auto zoom on first collector
                    if (!firstCollectorShown) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newPos, 14f)); // ✅ Explicit float value
                        firstCollectorShown = true;
                    }
                } else {
                    animateMarkerSmoothly(existingMarker, newPos);
                }
            }
        });
    }

    /** 🚗 Smooth marker animation */
    private void animateMarkerSmoothly(Marker marker, LatLng newPosition) {
        if (marker == null || newPosition == null) return;

        final LatLng startPosition = marker.getPosition();
        final long duration = 1000;
        final long start = System.currentTimeMillis();
        Handler handler = new Handler();

        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - start;
                float t = Math.min(1, (float) elapsed / duration);
                double lat = (newPosition.latitude - startPosition.latitude) * t + startPosition.latitude;
                double lng = (newPosition.longitude - startPosition.longitude) * t + startPosition.longitude;
                marker.setPosition(new LatLng(lat, lng));

                if (t < 1.0) {
                    handler.postDelayed(this, 16);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (collectorListener != null) {
            collectorListener.remove();
            collectorListener = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mMap != null) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        mMap.setMyLocationEnabled(true);
                        getDeviceLocation();
                        listenToAllCollectors();
                    }
                }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
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
            startActivity(new Intent(this, ResidentAccountSettingActivity.class));
            return true;
        } else if (id == R.id.action_contact) {
            Toast.makeText(this, "Contact Us clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_about) {
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            sessionManager.logoutResident();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ResidentLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }
}
