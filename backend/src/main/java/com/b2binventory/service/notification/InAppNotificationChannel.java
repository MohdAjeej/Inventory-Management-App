package com.b2binventory.service.notification;

import com.b2binventory.entity.Notification;
import org.springframework.stereotype.Component;

/** Notifications are stored by NotificationService; the in-app channel needs no extra delivery step. */
@Component
public class InAppNotificationChannel implements NotificationChannel {

    @Override
    public void deliver(Notification notification) {
        // Persisted rows are fetched by the app through /api/notifications.
    }
}
