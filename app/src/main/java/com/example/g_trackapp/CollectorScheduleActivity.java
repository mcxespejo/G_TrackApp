package com.example.g_trackapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CollectorScheduleActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    private TextView tvScheduleMon, tvScheduleTue, tvScheduleWed, tvScheduleThu,
            tvScheduleFri, tvScheduleSat, tvScheduleSun;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collector_schedule);

        db = FirebaseFirestore.getInstance();

        // TextViews for each day
        tvScheduleMon = findViewById(R.id.tvScheduleMon);
        tvScheduleTue = findViewById(R.id.tvScheduleTue);
        tvScheduleWed = findViewById(R.id.tvScheduleWed);
        tvScheduleThu = findViewById(R.id.tvScheduleThu);
        tvScheduleFri = findViewById(R.id.tvScheduleFri);
        tvScheduleSat = findViewById(R.id.tvScheduleSat);
        tvScheduleSun = findViewById(R.id.tvScheduleSun);

        // Example: hardcoding "malabon", but you could replace with logged-in collector’s assigned city
        String location = "malabon";

        // 🔹 Fetch schedule from Firestore or create default
        loadSchedule(location);

        // Navigation buttons
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, CollectorMainMenuActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnAlarm).setOnClickListener(v ->
                Toast.makeText(this, "Alarm clicked", Toast.LENGTH_SHORT).show()
        );

        // Menu button
        findViewById(R.id.btnMenu).setOnClickListener(v -> showPopupMenu((ImageView) v));
    }

    private void loadSchedule(String location) {
        db.collection("collector_schedules")
                .document(location.toLowerCase())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        Map<String, Object> data = document.getData();
                        if (data != null) {
                            displaySchedule(data);
                        }
                    } else {
                        // Generate default if none exists
                        Map<String, String> weeklySchedule = generateWeeklySchedule(location);
                        db.collection("collector_schedules")
                                .document(location.toLowerCase())
                                .set(weeklySchedule)
                                .addOnSuccessListener(aVoid ->
                                        Toast.makeText(this, "Default schedule saved ✅", Toast.LENGTH_SHORT).show()
                                )
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Error saving schedule ❌", Toast.LENGTH_SHORT).show()
                                );
                        displaySchedule(weeklySchedule);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error fetching schedule ❌", Toast.LENGTH_SHORT).show()
                );
    }

    private void displaySchedule(Map<String, ?> schedule) {
        tvScheduleMon.setText("Monday: " + schedule.get("Monday"));
        tvScheduleTue.setText("Tuesday: " + schedule.get("Tuesday"));
        tvScheduleWed.setText("Wednesday: " + schedule.get("Wednesday"));
        tvScheduleThu.setText("Thursday: " + schedule.get("Thursday"));
        tvScheduleFri.setText("Friday: " + schedule.get("Friday"));
        tvScheduleSat.setText("Saturday: " + schedule.get("Saturday"));
        tvScheduleSun.setText("Sunday: " + schedule.get("Sunday"));
    }

    /**
     * Default schedules
     */
    private Map<String, String> generateWeeklySchedule(String location) {
        Map<String, String> schedule = new HashMap<>();

        switch (location.toLowerCase()) {
            case "malabon":
                schedule.put("Monday", "No Collection");
                schedule.put("Tuesday", "7:00 AM");
                schedule.put("Wednesday", "No Collection");
                schedule.put("Thursday", "No Collection");
                schedule.put("Friday", "2:00 PM");
                schedule.put("Saturday", "No Collection");
                schedule.put("Sunday", "8:00 AM");
                break;

            case "navotas":
                schedule.put("Monday", "6:00 AM");
                schedule.put("Tuesday", "No Collection");
                schedule.put("Wednesday", "No Collection");
                schedule.put("Thursday", "1:00 PM");
                schedule.put("Friday", "No Collection");
                schedule.put("Saturday", "7:00 AM");
                schedule.put("Sunday", "No Collection");
                break;

            case "caloocan":
                schedule.put("Monday", "No Collection");
                schedule.put("Tuesday", "10:00 AM");
                schedule.put("Wednesday", "4:00 PM");
                schedule.put("Thursday", "No Collection");
                schedule.put("Friday", "11:00 AM");
                schedule.put("Saturday", "No Collection");
                schedule.put("Sunday", "9:00 AM");
                break;

            default:
                schedule.put("Monday", "8:00 AM");
                schedule.put("Tuesday", "No Collection");
                schedule.put("Wednesday", "2:00 PM");
                schedule.put("Thursday", "No Collection");
                schedule.put("Friday", "9:00 AM");
                schedule.put("Saturday", "No Collection");
                schedule.put("Sunday", "3:00 PM");
                break;
        }
        return schedule;
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
            getSharedPreferences("CollectorPrefs", MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, CollectorLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }
}
