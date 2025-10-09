package com.example.g_trackapp;

import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.LinkedList;
import java.util.Queue;

public class ResidentNotificationActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String residentId;
    private final Queue<String> notificationQueue = new LinkedList<>();
    private boolean isShowing = false;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseFirestore.getInstance();

        ResidentSessionManager sessionManager = new ResidentSessionManager(this);
        residentId = sessionManager.getResidentId();

        listenForNotifications();
    }

    private void listenForNotifications() {
        db.collection("notifications")
                .whereEqualTo("residentId", residentId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            String message = dc.getDocument().getString("message");
                            boolean read = Boolean.TRUE.equals(dc.getDocument().getBoolean("read"));

                            if (!read && message != null && !message.isEmpty()) {
                                notificationQueue.add(message);

                                // Mark as read after adding
                                dc.getDocument().getReference().update("read", true);
                            }
                        }
                    }

                    if (!isShowing) showNextNotification();
                });
    }

    private void showNextNotification() {
        if (notificationQueue.isEmpty()) {
            isShowing = false;
            return;
        }

        isShowing = true;
        String message = notificationQueue.poll();

        // Inflate custom toast layout
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_toast_notification, null);
        TextView text = layout.findViewById(R.id.toast_message);
        text.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.setGravity(Gravity.TOP | Gravity.END, 40, 150); // top-right corner
        toast.show();

        // Wait before showing next
        handler.postDelayed(this::showNextNotification, 3500);
    }
}
