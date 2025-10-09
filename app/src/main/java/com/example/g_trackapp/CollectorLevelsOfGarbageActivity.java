package com.example.g_trackapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.CollectionReference;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class CollectorLevelsOfGarbageActivity extends AppCompatActivity {

    private Button btnLow, btnMedium, btnHigh, btnExtreme, btnConfirm;
    private TextView tvSelectedAddress;
    private String selectedLevel = "";
    private String selectedAddress = "";
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private String currentCollectorId;
    private String collectorFullName = "Unknown";

    private static final int LOCATION_PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collector_levels_of_garbage);

        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        tvSelectedAddress = findViewById(R.id.tvSelectedAddress);
        btnLow = findViewById(R.id.btnLow);
        btnMedium = findViewById(R.id.btnMedium);
        btnHigh = findViewById(R.id.btnHigh);
        btnExtreme = findViewById(R.id.btnExtreme);
        btnConfirm = findViewById(R.id.btnConfirm);

        // Load collector ID from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        currentCollectorId = prefs.getString("collectorId", null);
        if (currentCollectorId != null) {
            fetchCollectorFullName(currentCollectorId);
        } else {
            Toast.makeText(this, "Collector not logged in!", Toast.LENGTH_SHORT).show();
        }

        // Garbage level buttons
        btnLow.setOnClickListener(v -> selectLevel("Low"));
        btnMedium.setOnClickListener(v -> selectLevel("Medium"));
        btnHigh.setOnClickListener(v -> selectLevel("High"));
        btnExtreme.setOnClickListener(v -> selectLevel("Extreme"));

        // Confirm button
        btnConfirm.setOnClickListener(v -> {
            if (selectedLevel.isEmpty()) {
                Toast.makeText(this, "Please select a garbage level!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedAddress.isEmpty()) {
                Toast.makeText(this, "Address not available", Toast.LENGTH_SHORT).show();
                return;
            }
            saveToFirestore(selectedLevel, selectedAddress, collectorFullName);
        });

        // Top-right menu
        ImageView btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu != null) btnMenu.setOnClickListener(v -> showPopupMenu(btnMenu));

        // Bottom navigation
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, CollectorMainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        findViewById(R.id.btnAlarm).setOnClickListener(v ->
                Toast.makeText(this, "Alarm clicked", Toast.LENGTH_SHORT).show()
        );
    }

    private void selectLevel(String level) {
        selectedLevel = level;
        getCurrentAddress();
    }

    private void getCurrentAddress() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        selectedAddress = addresses.get(0).getAddressLine(0);
                        tvSelectedAddress.setText(selectedAddress);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to get address", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchCollectorFullName(String collectorId) {
        db.collection("collectors").document(collectorId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String firstName = doc.getString("firstName");
                        String lastName = doc.getString("lastName");
                        collectorFullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch collector info", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveToFirestore(String level, String address, String collectorName) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        Map<String, Object> data = new HashMap<>();
        data.put("collectorName", collectorName);
        data.put("garbageLevel", level);
        data.put("location", address);
        data.put("date", date);
        data.put("time", time);

        CollectionReference colRef = db.collection("garbagelevel");
        colRef.add(data).addOnSuccessListener(docRef -> {
            Toast.makeText(this, "Successfully saved Garbage Level!", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
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
            getSharedPreferences("UserPrefs", MODE_PRIVATE).edit().clear().apply();
            Intent intent = new Intent(this, CollectorLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentAddress();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
