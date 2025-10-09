package com.example.g_trackapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class CollectorLoginActivity extends AppCompatActivity {

    private static final String TAG = "CollectorLoginActivity";

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
            String savedUsername = sessionManager.getUsername();
            Log.d(TAG, "Auto-login for collector: " + savedUsername);
            Intent intent = new Intent(this, CollectorLandingPageActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // 🔹 Login button click
        btnLogin.setOnClickListener(v -> {
            String username = (etUsername.getText() != null) ? etUsername.getText().toString().trim() : "";
            String password = (etPassword.getText() != null) ? etPassword.getText().toString().trim() : "";

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

                // 🔹 Navigate to landing page
                Intent intent = new Intent(this, CollectorLandingPageActivity.class);
                startActivity(intent);
                finish();

            } else {
                Log.w(TAG, "Wrong password for collector: " + username);
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.w(TAG, "Collector not found: " + username);
            Toast.makeText(this, "Collector not found", Toast.LENGTH_SHORT).show();
        }
    }
}
