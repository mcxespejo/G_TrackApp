package com.example.g_trackapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Toast;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

public class CollectorFeedbackActivity extends AppCompatActivity {

    Spinner spinnerFeedbackType;
    EditText etComments;
    Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collector_feedback);

        // Bind views
        spinnerFeedbackType = findViewById(R.id.spinnerFeedbackType);
        etComments = findViewById(R.id.etComments);
        btnSubmit = findViewById(R.id.btnSubmit);

        // Dropdown options
        String[] feedbackOptions = {"System Issue", "Route Suggestion", "General Feedback"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, feedbackOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFeedbackType.setAdapter(adapter);

        // --- Top-right menu using PopupMenu ---
        ImageView btnMenu = findViewById(R.id.btnMenu); // Add this ImageView in XML
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> showPopupMenu(btnMenu));
        }

        // --- Bottom Navigation Buttons ---
        // Back Button
        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Home Button
        ImageView btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, CollectorMainMenuActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        // Alarm Button
        ImageView btnAlarm = findViewById(R.id.btnAlarm);
        if (btnAlarm != null) {
            btnAlarm.setOnClickListener(v ->
                    Toast.makeText(this, "Collector alarm clicked", Toast.LENGTH_SHORT).show()
            );
        }

        // Submit button logic
        btnSubmit.setOnClickListener(v -> {
            String feedbackType = spinnerFeedbackType.getSelectedItem().toString();
            String comments = etComments.getText().toString().trim();

            if (comments.isEmpty()) {
                Toast.makeText(this, "Please enter comments", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Feedback submitted: " + feedbackType + "\n" + comments,
                        Toast.LENGTH_LONG).show();
                etComments.setText("");
            }
        });
    }
    private void showPopupMenu(ImageView anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_top_right, popup.getMenu());
        popup.setOnMenuItemClickListener(this::handleMenuClick);
        popup.show();
    }

    private boolean handleMenuClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            // ✅ Route to CollectorAccountSettingActivity
            Intent intent = new Intent(this, CollectorAccountSettingActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_contact) {
            Toast.makeText(this, "Contact Us clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_about) {
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            // 🔹 Clear session
            getSharedPreferences("ResidentPrefs", MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();

            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

            // 🔹 Redirect back to login screen
            Intent intent = new Intent(this, ResidentLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }
}
