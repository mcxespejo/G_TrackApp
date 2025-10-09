package com.example.g_trackapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

public class ResidentLandingPageActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private static final String TAG = "ResidentLandingPage";

    private ResidentSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new ResidentSessionManager(this);

        // 🔹 Check login status
        if (!sessionManager.isLoggedIn()) {
            // 🚪 Not logged in → go to login screen
            Intent intent = new Intent(this, ResidentLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return; // stop executing landing page code
        }

        // ✅ User is logged in → continue showing landing page
        setContentView(R.layout.activity_resident_landing_page);

        // 🔹 UI elements
        TextView welcomeText = findViewById(R.id.welcomeText);
        ImageView btnBack = findViewById(R.id.btnBack);
        Button btnGetStarted = findViewById(R.id.btnGetStarted);

        // Get name from session
        String firstName = sessionManager.getFirstName();
        String lastName = sessionManager.getLastName();

        if (firstName != null && lastName != null) {
            welcomeText.setText("Welcome to G-Track, " + firstName + " " + lastName + "!");
        } else {
            welcomeText.setText("Welcome to G-Track!");
        }

        Log.d(TAG, "Resident from session: " + firstName + " " + lastName);

        // Setup Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.residentMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Back Button
        btnBack.setOnClickListener(v -> finish());

        // Get Started Button → Main Menu
        btnGetStarted.setOnClickListener(v -> {
            Intent intent = new Intent(this, ResidentMainMenuActivity.class);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }
}
