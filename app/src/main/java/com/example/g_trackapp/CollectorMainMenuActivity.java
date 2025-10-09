package com.example.g_trackapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

public class CollectorMainMenuActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private CollectorSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collector_main_menu);

        // 🔹 Initialize Session Manager
        sessionManager = new CollectorSessionManager(this);

        // --- Load Google Map ---
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.collectorMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // --- Top-right menu using PopupMenu ---
        ImageView btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> showPopupMenu(btnMenu));
        }

        // --- Bottom Navigation Buttons ---
        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> {
            // Just go back
            finish();
        });

        ImageView btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, CollectorMainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        ImageView btnAlarm = findViewById(R.id.btnAlarm);
        if (btnAlarm != null) btnAlarm.setOnClickListener(v ->
                Toast.makeText(this, "Alarm clicked", Toast.LENGTH_SHORT).show()
        );

        // --- Buttons Section ---
        Button btnLevelsOfGarbage = findViewById(R.id.btnLevelsOfGarbage);
        if (btnLevelsOfGarbage != null) {
            btnLevelsOfGarbage.setOnClickListener(v -> {
                startActivity(new Intent(this, CollectorLevelsOfGarbageActivity.class));
            });
        }

        Button btnViewRouteSuggestion = findViewById(R.id.btnViewRouteSuggestion);
        if (btnViewRouteSuggestion != null) {
            btnViewRouteSuggestion.setOnClickListener(v -> {
                startActivity(new Intent(this, CollectorRouteSuggestionActivity.class));
            });
        }

        // 🔹 New Schedule Button → Opens Firestore-based weekly schedule
        Button btnSchedule = findViewById(R.id.btnSchedule);
        if (btnSchedule != null) {
            btnSchedule.setOnClickListener(v -> {
                Intent intent = new Intent(this, CollectorScheduleActivity.class);
                startActivity(intent);
            });
        }

        Button btnFeedback = findViewById(R.id.btnFeedback);
        if (btnFeedback != null) {
            btnFeedback.setOnClickListener(v -> {
                startActivity(new Intent(this, CollectorFeedbackActivity.class));
            });
        }
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
            // 🔹 Clear session via CollectorSessionManager
            sessionManager.clearSession();

            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

            // 🔹 Redirect back to login screen
            Intent intent = new Intent(this, CollectorLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }
}
