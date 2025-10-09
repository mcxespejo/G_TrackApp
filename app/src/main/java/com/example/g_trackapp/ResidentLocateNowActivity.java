package com.example.g_trackapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class ResidentLocateNowActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private ResidentSessionManager sessionManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_locate_now);

        sessionManager = new ResidentSessionManager(this);

        // Initialize fused location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // --- Request Location Permission Early ---
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }

        // --- Initialize Map ---
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // --- Buttons for Actions ---
        findViewById(R.id.btnYourLocation).setOnClickListener(v -> getDeviceLocation());

        findViewById(R.id.btnDropOffLocation).setOnClickListener(v ->
                Toast.makeText(this, "Drop-off location selected", Toast.LENGTH_SHORT).show()
        );

        // --- Top Bar Navigation ---
        findViewById(R.id.btnMenu).setOnClickListener(v -> showPopupMenu((ImageView) v));

        // --- Bottom Navigation Bar ---
        ImageView btnBackBottom = findViewById(R.id.btnBack);
        if (btnBackBottom != null) {
            btnBackBottom.setOnClickListener(v -> finish());
        }

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

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Enable zoom controls
        mMap.getUiSettings().setZoomControlsEnabled(true);

        // Request location or enable My Location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            getDeviceLocation();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void getDeviceLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null && mMap != null) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.clear();
                            mMap.addMarker(new MarkerOptions().position(currentLatLng).title("You are here"));
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16));
                        } else {
                            Toast.makeText(this, "Unable to detect location", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    if (mMap != null) {
                        mMap.setMyLocationEnabled(true);
                        getDeviceLocation();
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
            // ✅ Route to ResidentAccountSettingActivity
            Intent intent = new Intent(this, ResidentAccountSettingActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_contact) {
            Toast.makeText(this, "Contact Us clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_about) {
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            // 🔹 Use ResidentSessionManager to log out
            sessionManager.logoutResident();

            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

            // 🔹 Redirect back to login screen
            Intent intent = new Intent(this, ResidentLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }
}
