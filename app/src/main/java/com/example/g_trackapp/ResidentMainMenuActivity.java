package com.example.g_trackapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResidentMainMenuActivity extends AppCompatActivity {

    private ResidentSessionManager sessionManager;
    private FirebaseFirestore db;
    private ListenerRegistration notificationListener;

    private TextView txtBadgeCount;
    private ImageView btnMenu, btnNotification, btnAlarm;

    // Popup components
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
        btnAlarm = findViewById(R.id.btnAlarm); // Alarm button

        // Navigation buttons
        findViewById(R.id.btnLocateNow).setOnClickListener(v ->
                startActivity(new Intent(this, ResidentLocateNowActivity.class))
        );
        findViewById(R.id.btnSchedule).setOnClickListener(v ->
                startActivity(new Intent(this, ResidentScheduleActivity.class))
        );
        findViewById(R.id.btnFeedback).setOnClickListener(v ->
                startActivity(new Intent(this, ResidentFeedbackActivity.class))
        );
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, ResidentMainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // Notification button
        btnNotification.setOnClickListener(v -> {
            if (!popupVisible) showNotificationPopup();
        });

        // Alarm button
        btnAlarm.setOnClickListener(v -> {
            scheduleAlarm("G-Track Reminder", "Check your schedule or notifications!", 5);
            Toast.makeText(this, "Alarm set for 5 seconds later", Toast.LENGTH_SHORT).show();
        });

        // Top-right menu
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> showPopupMenu(btnMenu));
        }

        // Listen for notifications
        listenToNotifications();
    }

    /**
     * 🔥 Real-time Firestore notification listener (filtered by username)
     */
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

    /**
     * 📩 Show notification popup with slide-down animation
     */
    private void showNotificationPopup() {
        if (popupVisible) return;

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        popupRoot = (FrameLayout) inflater.inflate(R.layout.layout_notification_popup, null);

        popupContainer = popupRoot.findViewById(R.id.notificationContainer);
        recyclerNotifications = popupRoot.findViewById(R.id.recyclerNotifications);

        if (popupContainer == null || recyclerNotifications == null) {
            Toast.makeText(this, "Popup layout missing required views.", Toast.LENGTH_SHORT).show();
            return;
        }

        recyclerNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(notificationList);
        recyclerNotifications.setAdapter(adapter);

        popupRoot.setAlpha(0f);
        popupRoot.setTranslationY(-200f);

        FrameLayout rootLayout = findViewById(android.R.id.content);
        rootLayout.addView(popupRoot);

        popupRoot.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start();

        popupVisible = true;

        popupRoot.setOnClickListener(v -> hideNotificationPopup());
        popupContainer.setOnClickListener(v -> {}); // Prevent dismissal when tapping inside

        loadNotifications();
    }

    /**
     * ❌ Hide notification popup with slide-up animation
     */
    private void hideNotificationPopup() {
        if (!popupVisible || popupRoot == null) return;

        popupVisible = false;

        popupRoot.animate()
                .alpha(0f)
                .translationY(-popupRoot.getHeight())
                .setDuration(300)
                .withEndAction(() -> {
                    FrameLayout rootLayout = findViewById(android.R.id.content);
                    if (popupRoot.getParent() != null) {
                        rootLayout.removeView(popupRoot);
                    }
                    popupRoot = null;
                })
                .start();
    }

    /**
     * 🧹 Load notifications from Firestore
     */
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
                        String title = doc.getString("title");
                        String message = doc.getString("message");
                        boolean isRead = Boolean.TRUE.equals(doc.getBoolean("isRead"));
                        Timestamp timestamp = doc.getTimestamp("timestamp");
                        notificationList.add(new NotificationModel(message, username, false, timestamp));
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show());
    }

    private void dismissPopup() {
        if (!popupVisible) return;
        popupVisible = false;

        popupRoot.animate()
                .alpha(0f)
                .translationY(-popupRoot.getHeight())
                .setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        ((FrameLayout) popupRoot.getParent()).removeView(popupRoot);
                    }
                })
                .start();
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

    @Override
    public void onBackPressed() {
        if (popupVisible) {
            dismissPopup();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationListener != null) notificationListener.remove();
    }

    /**
     * 🔔 Schedule a local alarm/notification
     */
    private void scheduleAlarm(String title, String message, int delayInSeconds) {
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
        long triggerAt = System.currentTimeMillis() + (delayInSeconds * 1000);
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    }
}
