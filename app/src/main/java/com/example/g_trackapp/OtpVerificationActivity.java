package com.example.g_trackapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

public class OtpVerificationActivity extends AppCompatActivity {

    private EditText otpInput;
    private Button verifyOtpBtn;
    private String username;
    private String newPassword;
    private String userType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        otpInput = findViewById(R.id.et_otp);
        verifyOtpBtn = findViewById(R.id.btn_verify_otp);

        // Get data from intent
        Intent intent = getIntent();
        username = intent.getStringExtra("username");
        newPassword = intent.getStringExtra("newPassword");
        userType = intent.getStringExtra("userType");

        verifyOtpBtn.setOnClickListener(v -> {
            String enteredOtp = otpInput.getText().toString().trim();
            if (enteredOtp.isEmpty()) {
                Toast.makeText(this, "Please enter OTP", Toast.LENGTH_SHORT).show();
                return;
            }

            verifyOtpWithCloudFunction(username, enteredOtp, newPassword, userType);
        });
    }

    private void verifyOtpWithCloudFunction(String username, String otp, String newPassword, String userType) {
        // Example of calling Cloud Function
        com.google.firebase.functions.FirebaseFunctions.getInstance()
                .getHttpsCallable("verifyOtpAndResetPassword")
                .call(new java.util.HashMap<String, Object>() {{
                    put("username", username);
                    put("otp", otp);
                    put("newPassword", newPassword);
                    put("userType", userType);
                }})
                .addOnSuccessListener(result -> {
                    java.util.Map<String, Object> data = (java.util.Map<String, Object>) result.getData();
                    boolean success = Boolean.TRUE.equals(data.get("success"));
                    if (success) {
                        Toast.makeText(OtpVerificationActivity.this, "Password reset successful!", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(OtpVerificationActivity.this, ResidentLoginActivity.class));
                        finish();
                    } else {
                        Toast.makeText(OtpVerificationActivity.this, "Invalid OTP or expired", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(OtpVerificationActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
