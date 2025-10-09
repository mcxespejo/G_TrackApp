package com.example.g_trackapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This line links the Java code to the XML layout file
        setContentView(R.layout.activity_welcome);

        // Find the views from the XML layout using their defined IDs
        Button loginResidentButton = findViewById(R.id.btn_login_resident);
        Button loginCollectorButton = findViewById(R.id.btn_login_collector);
        TextView signUpPrompt = findViewById(R.id.tv_signup_prompt);

        // Set up a click listener for the "Log In As Resident" button
        if (loginResidentButton != null) {
            loginResidentButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Start the ResidentLoginActivity (assuming you have this class)
                    Intent intent = new Intent(WelcomeActivity.this, ResidentLoginActivity.class);
                    startActivity(intent);
                }
            });
        }

        // Set up a click listener for the "Log In As Collector" button
        if (loginCollectorButton != null) {
            loginCollectorButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Start the CollectorLoginActivity (assuming you have this class)
                    Intent intent = new Intent(WelcomeActivity.this, CollectorLoginActivity.class);
                    startActivity(intent);
                }
            });
        }

        // Set up a click listener for the "Sign up" TextView
        if (signUpPrompt != null) {
            signUpPrompt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Start the RegisterActivity (assuming you have this class)
                    Intent intent = new Intent(WelcomeActivity.this, ResidentRegisterActivity.class);
                    startActivity(intent);
                }
            });
        }
    }
}
