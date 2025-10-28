package com.example.g_trackapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CollectorLoginActivity extends AppCompatActivity {

    private static final String TAG = "CollectorLoginActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final long RESET_CODE_EXPIRATION_MS = 5 * 60 * 1000; // 5 minutes

    private FirebaseFirestore db;
    private FirebaseFunctions functions;
    private CollectorSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collector_login);

        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance();
        sessionManager = new CollectorSessionManager(this);

        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);

        // 🔹 Auto-login
        if (sessionManager.isLoggedIn()) {
            startLocationServiceWithPermissionCheck();
            goToLandingPage();
            return;
        }

        // 🔹 Login
        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
                return;
            }

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

        // 🔹 Forgot Password
        tvForgotPassword.setOnClickListener(v -> showUsernameDialog());
        btnBack.setOnClickListener(v -> finish());
    }

    // Step 1: Ask for username
    private void showUsernameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");

        final EditText inputUsername = new EditText(this);
        inputUsername.setHint("Enter your username");
        builder.setView(inputUsername);

        builder.setPositiveButton("Next", (dialog, which) -> {
            String username = inputUsername.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(this, "Please enter your username", Toast.LENGTH_SHORT).show();
                return;
            }
            sendResetCode(username);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void sendResetCode(String username) {
        db.collection("collectors")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                    String phone = document.getString("phone");
                    String docId = document.getId();

                    if (phone == null || phone.isEmpty()) {
                        Toast.makeText(this, "No phone number found for this account", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("phone", phone);

                    functions
                            .getHttpsCallable("sendVerificationCode")
                            .call(data)
                            .addOnSuccessListener((HttpsCallableResult result) -> {
                                Toast.makeText(this, "Verification code sent via SMS.", Toast.LENGTH_LONG).show();
                                showVerificationDialog(username);
                            })
                            .addOnFailureListener((OnFailureListener) e -> {
                                Log.w(TAG, "Cloud function sendVerificationCode failed, using fallback", e);
                                simulateSendResetCode(username, phone, docId);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error querying Firestore", e);
                    Toast.makeText(this, "Error connecting to Firestore", Toast.LENGTH_SHORT).show();
                });
    }

    private void simulateSendResetCode(String username, String phone, String docId) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        long timestamp = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("resetCode", code);
        updates.put("resetTimestamp", timestamp);

        DocumentReference ref = db.collection("collectors").document(docId);
        ref.update(updates)
                .addOnSuccessListener(aVoid -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Verification Code Sent (Simulated)")
                            .setMessage("Code sent to " + phone + "\n\n(for testing: " + code + ")")
                            .setPositiveButton("OK", (d, w) -> showVerificationDialog(username))
                            .show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating reset code", e);
                    Toast.makeText(this, "Failed to send code", Toast.LENGTH_SHORT).show();
                });
    }

    private void showVerificationDialog(String username) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Verify Code & Reset Password");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputCode = new EditText(this);
        inputCode.setHint("Enter verification code");
        layout.addView(inputCode);

        final EditText inputNewPassword = new EditText(this);
        inputNewPassword.setHint("Enter new password");
        layout.addView(inputNewPassword);

        builder.setView(layout);

        builder.setPositiveButton("Reset", (dialog, which) -> {
            String codeEntered = inputCode.getText().toString().trim();
            String newPassword = inputNewPassword.getText().toString().trim();

            if (codeEntered.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            verifyCodeWithCloudFunction(username, codeEntered, newPassword);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void verifyCodeWithCloudFunction(String username, String enteredCode, String newPassword) {
        db.collection("collectors")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener((QuerySnapshot querySnapshot) -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                    String phone = doc.getString("phone");
                    String docId = doc.getId();

                    if (phone == null || phone.isEmpty()) {
                        Toast.makeText(this, "No phone number found for this account", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("phone", phone);
                    payload.put("code", enteredCode);
                    payload.put("newPassword", newPassword); // ✅ include new password
                    payload.put("userType", "collector"); // ✅ pass user type

                    functions.getHttpsCallable("updatePassword")
                            .call(payload)
                            .addOnSuccessListener((HttpsCallableResult result) -> {
                                Map<String, Object> data = (Map<String, Object>) result.getData();
                                boolean success = Boolean.TRUE.equals(data.get("success"));
                                if (success) {
                                    Toast.makeText(this, "Password reset successfully!", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Invalid code or failed to update.", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener((OnFailureListener) e -> {
                                Log.w(TAG, "updatePassword function failed, fallback to Firestore", e);
                                verifyAndResetPasswordFallback(docId, enteredCode, newPassword);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user for verification", e);
                    Toast.makeText(this, "Error connecting to Firestore", Toast.LENGTH_SHORT).show();
                });
    }

    private void verifyAndResetPasswordFallback(String docId, String enteredCode, String newPassword) {
        db.collection("collectors").document(docId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String storedCode = documentSnapshot.getString("resetCode");
                    Long timestamp = documentSnapshot.getLong("resetTimestamp");

                    if (storedCode == null || timestamp == null) {
                        Toast.makeText(this, "No active reset code. Try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long now = System.currentTimeMillis();
                    if (!storedCode.equals(enteredCode)) {
                        Toast.makeText(this, "Invalid code", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (now - timestamp > RESET_CODE_EXPIRATION_MS) {
                        Toast.makeText(this, "Code expired. Please request a new one.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("password", newPassword);
                    updates.put("resetCode", null);
                    updates.put("resetTimestamp", null);

                    db.collection("collectors").document(docId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Password reset successfully!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error updating password", e);
                                Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error verifying code (fallback)", e);
                    Toast.makeText(this, "Error connecting to Firestore", Toast.LENGTH_SHORT).show();
                });
    }

    // 🔹 Login + Location Logic

    private void handleLoginResult(QuerySnapshot querySnapshot, String username, String enteredPassword) {
        if (!querySnapshot.isEmpty()) {
            DocumentSnapshot document = querySnapshot.getDocuments().get(0);
            String storedPassword = document.getString("password");

            if (storedPassword != null && storedPassword.equals(enteredPassword)) {
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                sessionManager.saveCollectorDetails(document.getId(), username,
                        document.getString("firstName"), document.getString("lastName"));
                startLocationServiceWithPermissionCheck();
                goToLandingPage();
            } else {
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Collector not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void startLocationServiceWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        } else {
            startCollectorLocationService();
        }
    }

    private void startCollectorLocationService() {
        Intent locationService = new Intent(this, CollectorLocationService.class);
        locationService.setAction("START_LOCATION_UPDATES");
        ContextCompat.startForegroundService(this, locationService);
    }

    private void goToLandingPage() {
        Intent intent = new Intent(this, CollectorLandingPageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetwork = cm != null ? cm.getActiveNetworkInfo() : null;
        return activeNetwork != null && activeNetwork.isConnected();
    }
}
