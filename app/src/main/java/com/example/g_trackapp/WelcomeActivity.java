package com.example.g_trackapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class WelcomeActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Link to the layout
        setContentView(R.layout.activity_welcome);

        // ✅ Ask for POST_NOTIFICATIONS permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION
                );
            }
        }

        // Find the views
        Button loginResidentButton = findViewById(R.id.btn_login_resident);
        Button loginCollectorButton = findViewById(R.id.btn_login_collector);
        TextView signUpPrompt = findViewById(R.id.tv_signup_prompt);

        // "Log In As Resident" button
        if (loginResidentButton != null) {
            loginResidentButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(WelcomeActivity.this, ResidentLoginActivity.class);
                    startActivity(intent);
                }
            });
        }

        // "Log In As Collector" button
        if (loginCollectorButton != null) {
            loginCollectorButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(WelcomeActivity.this, CollectorLoginActivity.class);
                    startActivity(intent);
                }
            });
        }

        // "Sign up" text
        if (signUpPrompt != null) {
            signUpPrompt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(WelcomeActivity.this, ResidentRegisterActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    // Optional: handle the user’s response to the permission prompt
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // ✅ Permission granted
            } else {
                // ⚠️ Permission denied – you might want to show a toast or dialog
            }
        }
    }
}
