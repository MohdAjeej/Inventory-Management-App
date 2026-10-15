package com.b2binventory.dto;

import java.time.Instant;
import java.util.Map;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long id, String type, String title, String body, Map<String, Object> payload, boolean read, Instant createdAt) {
    }

    public record UnreadCount(long count) {
    }
}
