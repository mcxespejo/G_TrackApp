package com.example.g_trackapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class CollectorLoginActivity extends AppCompatActivity {

    private static final String TAG = "CollectorLoginActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 100;

    private FirebaseFirestore db;
    private CollectorSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collector_login);

        db = FirebaseFirestore.getInstance();
        sessionManager = new CollectorSessionManager(this);

        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        ImageView btnBack = findViewById(R.id.btnBack);

        // 🔹 Auto-login if session exists
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Auto-login for collector: " + sessionManager.getUsername());
            startLocationServiceWithPermissionCheck();
            goToLandingPage();
            return;
        }

        // 🔹 Login button click
        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Checking Firestore for collector: " + username);

            db.collection("collectors")
                    .whereEqualTo("username", username)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(querySnapshot -> handleLoginResult(querySnapshot, username, password))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching collector", e);
                        Toast.makeText(this, "Error connecting to Firestore", Toast.LENGTH_SHORT).show();
                    });
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void handleLoginResult(QuerySnapshot querySnapshot, String username, String enteredPassword) {
        if (!querySnapshot.isEmpty()) {
            QueryDocumentSnapshot document = (QueryDocumentSnapshot) querySnapshot.getDocuments().get(0);
            String storedPassword = document.getString("password");

            if (storedPassword != null && storedPassword.equals(enteredPassword)) {
                Log.d(TAG, "Login successful for collector: " + username);
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

                // 🔹 Save session properly
                sessionManager.saveCollectorDetails(
                        document.getId(),
                        username,
                        document.getString("firstName"),
                        document.getString("lastName")
                );

                // 🔹 Start background location tracking service (after permission check)
                startLocationServiceWithPermissionCheck();

                // 🔹 Navigate to landing page
                goToLandingPage();

            } else {
                Log.w(TAG, "Wrong password for collector: " + username);
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.w(TAG, "Collector not found: " + username);
            Toast.makeText(this, "Collector not found", Toast.LENGTH_SHORT).show();
        }
    }

    // 🔹 Check for location permission before starting service
    private void startLocationServiceWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        } else {
            startCollectorLocationService();
        }
    }

    // 🔹 Start the actual location service
    private void startCollectorLocationService() {
        Intent locationService = new Intent(this, CollectorLocationService.class);
        locationService.setAction("START_LOCATION_UPDATES");
        ContextCompat.startForegroundService(this, locationService);
        Log.d(TAG, "CollectorLocationService started");
    }

    // 🔹 Handle runtime permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted — starting service");
                startCollectorLocationService();
            } else {
                Log.w(TAG, "Location permission denied — cannot start location updates");
                Toast.makeText(this, "Location permission is required for tracking.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void goToLandingPage() {
        Intent intent = new Intent(this, CollectorLandingPageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
