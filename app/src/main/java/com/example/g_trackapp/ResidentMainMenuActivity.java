package com.example.g_trackapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResidentMainMenuActivity extends AppCompatActivity {

    private ResidentSessionManager sessionManager;
    private FirebaseFirestore db;
    private ListenerRegistration notificationListener;

    private TextView txtBadgeCount;
    private ImageView btnMenu, btnNotification, btnAlarm;

    private FrameLayout popupRoot;
    private LinearLayout popupContainer;
    private RecyclerView recyclerNotifications;
    private NotificationAdapter adapter;
    private final List<NotificationModel> notificationList = new ArrayList<>();
    private boolean popupVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_main_menu);

        sessionManager = new ResidentSessionManager(this);
        db = FirebaseFirestore.getInstance();

        txtBadgeCount = findViewById(R.id.txtBadgeCount);
        btnNotification = findViewById(R.id.btnNotification);
        btnMenu = findViewById(R.id.btnMenu);
        btnAlarm = findViewById(R.id.btnAlarm);

        // 🔹 Navigation buttons
        findViewById(R.id.btnLocateNow).setOnClickListener(v ->
                startActivity(new Intent(this, ResidentLocateNowActivity.class)));
        findViewById(R.id.btnSchedule).setOnClickListener(v ->
                startActivity(new Intent(this, ResidentScheduleActivity.class)));
        findViewById(R.id.btnFeedback).setOnClickListener(v ->
                startActivity(new Intent(this, ResidentFeedbackActivity.class)));
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, ResidentMainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // 🔹 Notifications popup
        btnNotification.setOnClickListener(v -> {
            if (!popupVisible) showNotificationPopup();
        });

        // 🔹 Custom alarm button
        btnAlarm.setOnClickListener(v -> showTimePickerDialog());

        // 🔹 Menu
        btnMenu.setOnClickListener(v -> showPopupMenu(btnMenu));

        listenToNotifications();
    }

    // 🔔 Listen for Firestore notifications
    private void listenToNotifications() {
        String username = sessionManager.getUsername();
        if (username == null) return;

        notificationListener = db.collection("notifications")
                .whereEqualTo("username", username)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    int unreadCount = 0;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        Map<String, Object> notif = new HashMap<>(dc.getDocument().getData());
                        boolean isRead = notif.get("isRead") != null && (boolean) notif.get("isRead");
                        if (!isRead) unreadCount++;
                    }

                    if (unreadCount > 0) {
                        txtBadgeCount.setText(String.valueOf(unreadCount));
                        txtBadgeCount.setVisibility(TextView.VISIBLE);
                    } else {
                        txtBadgeCount.setVisibility(TextView.GONE);
                    }
                });
    }

    // 📩 Show popup notifications
    private void showNotificationPopup() {
        if (popupVisible) return;

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        popupRoot = (FrameLayout) inflater.inflate(R.layout.layout_notification_popup, null);

        popupContainer = popupRoot.findViewById(R.id.notificationContainer);
        recyclerNotifications = popupRoot.findViewById(R.id.recyclerNotifications);

        recyclerNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(notificationList);
        recyclerNotifications.setAdapter(adapter);

        popupRoot.setAlpha(0f);
        popupRoot.setTranslationY(-200f);

        FrameLayout rootLayout = findViewById(android.R.id.content);
        rootLayout.addView(popupRoot);

        popupRoot.animate().alpha(1f).translationY(0f).setDuration(300).start();
        popupVisible = true;

        popupRoot.setOnClickListener(v -> hideNotificationPopup());
        popupContainer.setOnClickListener(v -> {});

        loadNotifications();
    }

    // ❌ Hide popup
    private void hideNotificationPopup() {
        if (!popupVisible || popupRoot == null) return;
        popupVisible = false;

        popupRoot.animate()
                .alpha(0f)
                .translationY(-popupRoot.getHeight())
                .setDuration(300)
                .withEndAction(() -> {
                    FrameLayout rootLayout = findViewById(android.R.id.content);
                    rootLayout.removeView(popupRoot);
                    popupRoot = null;
                })
                .start();
    }

    // 🧹 Load notifications
    private void loadNotifications() {
        String username = sessionManager.getUsername();
        if (username == null) return;

        db.collection("notifications")
                .whereEqualTo("username", username)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    notificationList.clear();
                    for (var doc : snapshots.getDocuments()) {
                        String message = doc.getString("message");
                        Timestamp timestamp = doc.getTimestamp("timestamp");
                        notificationList.add(new NotificationModel(message, username, false, timestamp));
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show());
    }

    // ⚙️ Menu options
    private void showPopupMenu(ImageView anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_top_right, popup.getMenu());
        popup.setOnMenuItemClickListener(this::handleMenuClick);
        popup.show();
    }

    private boolean handleMenuClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, ResidentAccountSettingActivity.class));
            return true;
        } else if (id == R.id.action_contact) {
            Toast.makeText(this, "Contact Us clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_about) {
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            sessionManager.logoutResident();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ResidentLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }


    // ============================================
    // 🕒 CUSTOM ALARM FEATURE
    // ============================================

    private void showTimePickerDialog() {
        Calendar now = Calendar.getInstance();

        TimePickerDialog timePicker = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    Calendar alarmTime = Calendar.getInstance();
                    alarmTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    alarmTime.set(Calendar.MINUTE, minute);
                    alarmTime.set(Calendar.SECOND, 0);
                    alarmTime.set(Calendar.MILLISECOND, 0);

                    if (alarmTime.before(Calendar.getInstance())) {
                        alarmTime.add(Calendar.DATE, 1);
                    }

                    scheduleAlarmAt(
                            "G-Track Reminder",
                            "It's time to check your collection schedule!",
                            alarmTime
                    );

                    Toast.makeText(this,
                            "Alarm set for " + String.format("%02d:%02d", hourOfDay, minute),
                            Toast.LENGTH_SHORT).show();
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true
        );

        timePicker.show();
    }

    private void scheduleAlarmAt(String title, String message, Calendar alarmTime) {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("message", message);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            alarmTime.getTimeInMillis(),
                            pendingIntent
                    );
                } else {
                    Intent intentPermission = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    startActivity(intentPermission);
                    Toast.makeText(this, "Please grant exact alarm permission.", Toast.LENGTH_LONG).show();
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTime.getTimeInMillis(),
                        pendingIntent
                );
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationListener != null) notificationListener.remove();
    }

    @Override
    public void onBackPressed() {
        if (popupVisible) hideNotificationPopup();
        else super.onBackPressed();
    }
}
