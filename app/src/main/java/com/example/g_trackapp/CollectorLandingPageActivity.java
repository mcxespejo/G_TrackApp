package com.example.g_trackapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class CollectorLandingPageActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "CollectorLandingPage";
    private CollectorSessionManager sessionManager;
    private FirebaseFirestore db;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔹 Initialize session manager
        sessionManager = new CollectorSessionManager(this);

        // 🔹 Auto-login check
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "No valid session. Redirecting to login.");
            Intent intent = new Intent(this, CollectorLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_collector_landing_page);
        db = FirebaseFirestore.getInstance();

        TextView welcomeText = findViewById(R.id.welcomeText);
        ImageView btnBack = findViewById(R.id.btnBack);
        Button btnGetStarted = findViewById(R.id.btnGetStarted);

        // 🔹 Session-first username
        String username = sessionManager.getUsername();
        String firstName = sessionManager.getFirstName();
        String lastName = sessionManager.getLastName();

        // 🔹 Set initial welcome text from session
        if (firstName != null && lastName != null) {
            welcomeText.setText("Welcome to G-Track, " + firstName + " " + lastName + "!");
        } else if (username != null) {
            welcomeText.setText("Welcome to G-Track, " + username + "!");
        } else {
            welcomeText.setText("Welcome to G-Track!");
        }

        // 🔹 Optional Firestore fetch to refresh first/last name
        db.collection("collectors").document(sessionManager.getCollectorId())
                .get()
                .addOnSuccessListener(documentSnapshot -> handleFirestoreResult(documentSnapshot, welcomeText))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Firestore fetch failed (optional). Using session data.", e);
                });

        // 🔹 Map setup
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.collectorMap);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // 🔹 Back button
        btnBack.setOnClickListener(v -> finish());

        // 🔹 Get Started → Main Menu
        btnGetStarted.setOnClickListener(v -> {
            Intent intent = new Intent(this, CollectorMainMenuActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void handleFirestoreResult(@NonNull DocumentSnapshot doc, TextView welcomeText) {
        if (doc.exists()) {
            String firstName = doc.getString("firstName");
            String lastName = doc.getString("lastName");
            String username = doc.getString("username");

            if (firstName != null && lastName != null) {
                welcomeText.setText("Welcome to G-Track, " + firstName + " " + lastName + "!");
                // 🔹 Update session with latest data
                sessionManager.saveCollectorDetails(sessionManager.getCollectorId(), username, firstName, lastName);
                Log.d(TAG, "Session updated with Firestore data: " + firstName + " " + lastName);
            }
        } else {
            Log.w(TAG, "Firestore document not found. Keeping session data.");
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }
    }
}
