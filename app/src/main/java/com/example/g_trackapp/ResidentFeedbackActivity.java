package com.example.g_trackapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ResidentFeedbackActivity extends AppCompatActivity {

    Spinner spinnerFeedbackType;
    EditText etComments;
    Button btnSubmit;
    private ResidentSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_feedback);

        sessionManager = new ResidentSessionManager(this);

        spinnerFeedbackType = findViewById(R.id.spinnerFeedbackType);
        etComments = findViewById(R.id.etComments);
        btnSubmit = findViewById(R.id.btnSubmit);

        // Setup feedback type dropdown
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.feedback_types,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFeedbackType.setAdapter(adapter);

        // Submit feedback
        btnSubmit.setOnClickListener(v -> {
            String comments = etComments.getText().toString().trim();
            if (comments.isEmpty()) {
                Toast.makeText(this, "Please enter your comments.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Feedback submitted.", Toast.LENGTH_SHORT).show();
            etComments.setText("");
        });

        // Navigation buttons
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, ResidentMainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        findViewById(R.id.btnAlarm).setOnClickListener(v ->
                Toast.makeText(this, "Alarm clicked", Toast.LENGTH_SHORT).show()
        );

        // Menu button (top right)
        findViewById(R.id.btnMenu).setOnClickListener(v -> showPopupMenu((ImageView) v));
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
            // ✅ Route to ResidentAccountSettingActivity
            Intent intent = new Intent(this, ResidentAccountSettingActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_contact) {
            Toast.makeText(this, "Contact Us clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_about) {
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            // 🔹 Use ResidentSessionManager to log out
            sessionManager.logoutResident();

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
