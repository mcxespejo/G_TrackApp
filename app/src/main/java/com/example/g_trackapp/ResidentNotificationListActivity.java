package com.example.g_trackapp;

import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ResidentNotificationListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private FirebaseFirestore db;
    private String residentUsername;
    private List<NotificationModel> notificationList = new ArrayList<>();
    private LinearLayout notificationContainer;
    private FrameLayout rootLayout;
    private boolean isClosing = false;
    private ResidentSessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_notification_list);

        recyclerView = findViewById(R.id.recyclerNotifications);
        notificationContainer = findViewById(R.id.notificationContainer);
        rootLayout = findViewById(R.id.rootLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(notificationList);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        sessionManager = new ResidentSessionManager(this);
        residentUsername = sessionManager.getUsername(); // ✅ filter by username

        loadNotifications();

        // Animate slide down + fade in
        Animation slideDownFadeIn = AnimationUtils.loadAnimation(this, R.anim.slide_down_fade_in);
        notificationContainer.startAnimation(slideDownFadeIn);

        // Tap outside to close
        rootLayout.setOnClickListener(v -> {
            if (!isClosing) closePopup();
        });

        // Prevent closing when clicking inside the popup
        notificationContainer.setOnClickListener(v -> {});
    }

    private void loadNotifications() {
        if (residentUsername == null) return;

        db.collection("notifications")
                .whereEqualTo("username", residentUsername)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    notificationList.clear();
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            String message = dc.getDocument().getString("message");
                            Boolean isRead = dc.getDocument().getBoolean("isRead");
                            Timestamp timestamp = dc.getDocument().getTimestamp("timestamp");

                            if (message == null) message = "(No message)";
                            if (isRead == null) isRead = false;

                            notificationList.add(new NotificationModel(
                                    message,
                                    residentUsername,
                                    isRead,
                                    timestamp
                            ));
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void closePopup() {
        isClosing = true;
        Animation slideUpFadeOut = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_out);
        slideUpFadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                finish();
                overridePendingTransition(0, 0);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        notificationContainer.startAnimation(slideUpFadeOut);
    }

    @Override
    public void onBackPressed() {
        if (!isClosing) closePopup();
        else super.onBackPressed();
    }

    /** Utility method for formatting timestamp to string */
    public static String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "—";
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        return sdf.format(timestamp.toDate());
    }
}
