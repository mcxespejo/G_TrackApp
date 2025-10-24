package com.example.g_trackapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class ResidentLoginActivity extends AppCompatActivity {

    private static final String TAG = "ResidentLoginActivity";
    private FirebaseFirestore db;
    private ResidentSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_login);

        db = FirebaseFirestore.getInstance();
        sessionManager = new ResidentSessionManager(this);

        Button loginButton = findViewById(R.id.btn_login);
        TextView signUpPrompt = findViewById(R.id.tv_signup_prompt);
        TextView forgotPasswordLink = findViewById(R.id.tv_forgot_password);
        EditText usernameInput = findViewById(R.id.et_username);
        EditText passwordInput = findViewById(R.id.et_password);
        ImageView btnBack = findViewById(R.id.btnBack);

        // 🔹 Auto-login if already logged in
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Auto-login for resident: " + sessionManager.getUsername());
            goToResidentLandingPage();
            return;
        }

        // 🔹 Login Button Logic
        loginButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Checking Firestore for resident: " + username);

            db.collection("residents")
                    .whereEqualTo("username", username)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                            String storedPassword = document.getString("password");

                            if (storedPassword != null && storedPassword.equals(password)) {
                                Log.d(TAG, "Login successful for resident: " + username);
                                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

                                // ✅ Fetch fields (supporting multiple naming conventions)
                                String firstName = document.getString("firstname");
                                if (firstName == null) firstName = document.getString("firstName");

                                String lastName = document.getString("lastname");
                                if (lastName == null) lastName = document.getString("lastName");

                                String email = document.getString("email");
                                String contact = document.getString("phone");
                                String region = document.getString("region");
                                String city = document.getString("city");
                                String barangay = document.getString("barangay");

                                // ✅ Save session
                                sessionManager.saveResidentDetails(
                                        document.getId(),
                                        username,
                                        firstName != null ? firstName : "",
                                        lastName != null ? lastName : "",
                                        email != null ? email : "",
                                        contact != null ? contact : ""
                                );
                                sessionManager.saveExtraResidentInfo(region, city, barangay);

                                Log.d(TAG, "Saved resident session for: " + firstName + " " + lastName);

                                // 🔹 Save or refresh FCM Token
                                updateFcmToken(document.getId());

                                // 🚀 Redirect to landing page
                                goToResidentLandingPage();

                            } else {
                                Log.w(TAG, "Wrong password for resident: " + username);
                                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.w(TAG, "Resident not found: " + username);
                            Toast.makeText(this, "Resident not found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching resident", e);
                        Toast.makeText(this, "Error connecting to Firestore", Toast.LENGTH_SHORT).show();
                    });
        });

        // 🔹 Sign Up Redirect
        signUpPrompt.setOnClickListener(v -> {
            Log.d(TAG, "Sign Up link clicked.");
            startActivity(new Intent(this, ResidentRegisterActivity.class));
        });

        // 🔹 Forgot Password Placeholder
        forgotPasswordLink.setOnClickListener(v -> {
            Log.d(TAG, "Forgot Password link clicked.");
            // TODO: Implement forgot password
        });

        // 🔹 Back Button
        btnBack.setOnClickListener(v -> finish());
    }

    /**
     * 🔹 Save or update the FCM token for this resident
     */
    private void updateFcmToken(String residentId) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM token failed", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    if (token == null || token.isEmpty()) {
                        Log.w(TAG, "FCM token is null or empty");
                        return;
                    }

                    Map<String, Object> updateData = new HashMap<>();
                    updateData.put("fcmToken", token);

                    db.collection("residents")
                            .document(residentId)
                            .update(updateData)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ FCM token saved for resident: " + residentId))
                            .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to save FCM token", e));
                });
    }

    /**
     * 🔹 Redirect to Resident Landing Page
     */
    private void goToResidentLandingPage() {
        Intent intent = new Intent(this, ResidentLandingPageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
