package com.example.g_trackapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

public class ResetPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ResetPasswordActivity";

    private FirebaseFunctions functions;
    private EditText usernameInput;
    private Button verifyButton;
    private ImageView btnBack;

    private String userType = "collector"; // Default to collector

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        functions = FirebaseFunctions.getInstance();

        usernameInput = findViewById(R.id.et_username);
        verifyButton = findViewById(R.id.btn_verify_username);
        btnBack = findViewById(R.id.btnBack);

        // 🧭 Determine if resident or collector
        if (getIntent() != null && getIntent().hasExtra("userType")) {
            userType = getIntent().getStringExtra("userType");
        }

        btnBack.setOnClickListener(v -> finish());

        verifyButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(this, "Please enter your username", Toast.LENGTH_SHORT).show();
                return;
            }

            verifyUsername(username);
        });
    }

    /**
     * 🔹 Step 1: Verify username exists in Firestore via Cloud Function
     */
    private void verifyUsername(String username) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("userType", userType);

        functions.getHttpsCallable("verifyUserForReset")
                .call(data)
                .addOnSuccessListener(result -> {
                    Map<String, Object> res = (Map<String, Object>) result.getData();
                    boolean success = Boolean.TRUE.equals(res.get("success"));

                    if (success) {
                        Toast.makeText(this, "Username verified! Sending OTP...", Toast.LENGTH_SHORT).show();
                        sendOtp(username);
                    } else {
                        String message = (String) res.get("message");
                        Toast.makeText(this, message != null ? message : "User not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error verifying username", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * 🔹 Step 2: Send OTP via Firebase Function
     */
    private void sendOtp(String username) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("userType", userType);

        functions.getHttpsCallable("sendOtp")
                .call(data)
                .addOnSuccessListener(result -> {
                    Map<String, Object> res = (Map<String, Object>) result.getData();
                    boolean success = Boolean.TRUE.equals(res.get("success"));

                    if (success) {
                        Toast.makeText(this, "OTP sent successfully!", Toast.LENGTH_LONG).show();

                        // ✅ Move to OTP verification screen
                        Intent intent = new Intent(this, OtpVerificationActivity.class);
                        intent.putExtra("username", username);
                        intent.putExtra("userType", userType);
                        startActivity(intent);
                    } else {
                        String message = (String) res.get("message");
                        Toast.makeText(this, message != null ? message : "Failed to send OTP", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending OTP", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
