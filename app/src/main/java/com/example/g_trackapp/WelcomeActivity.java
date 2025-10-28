package com.example.g_trackapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class WelcomeActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // --- Initialize Permission Request Launcher ---
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Toast.makeText(this, "Notifications enabled ✅", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Notifications disabled ⚠️", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // --- Ask for Notification Permission (Android 13+) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission();
        }

        // --- Initialize UI elements ---
        Button loginResidentButton = findViewById(R.id.btn_login_resident);
        Button loginCollectorButton = findViewById(R.id.btn_login_collector);
        TextView signUpPrompt = findViewById(R.id.tv_signup_prompt);

        // --- Set up button listeners ---
        if (loginResidentButton != null) {
            loginResidentButton.setOnClickListener(v -> {
                startActivity(new Intent(WelcomeActivity.this, ResidentLoginActivity.class));
            });
        }

        if (loginCollectorButton != null) {
            loginCollectorButton.setOnClickListener(v -> {
                startActivity(new Intent(WelcomeActivity.this, CollectorLoginActivity.class));
            });
        }

        if (signUpPrompt != null) {
            signUpPrompt.setOnClickListener(v -> {
                startActivity(new Intent(WelcomeActivity.this, ResidentRegisterActivity.class));
            });
        }
    }

    // --- Request notification permission safely ---
    private void requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // --- Handle other future permissions (optional expansion) ---
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Reserved for future use (e.g., location, SMS)
    }
}
