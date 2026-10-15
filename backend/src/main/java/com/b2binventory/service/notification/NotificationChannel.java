package com.b2binventory.service.notification;

import com.b2binventory.entity.Notification;

/**
 * Delivery channel for notifications. The in-app channel persists them; a push channel
 * (e.g. FCM) can be added as another bean without changing callers.
 */
public interface NotificationChannel {

    void deliver(Notification notification);
}
