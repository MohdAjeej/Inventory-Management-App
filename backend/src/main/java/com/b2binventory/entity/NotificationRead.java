package com.b2binventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "notification_reads")
@IdClass(NotificationRead.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class NotificationRead {

    @Id
    private Long notificationId;

    @Id
    private Long membershipId;

    @Column(nullable = false)
    private Instant readAt = Instant.now();

    public NotificationRead(Long notificationId, Long membershipId) {
        this.notificationId = notificationId;
        this.membershipId = membershipId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long notificationId;
        private Long membershipId;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k
                    && Objects.equals(notificationId, k.notificationId)
                    && Objects.equals(membershipId, k.membershipId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notificationId, membershipId);
        }
    }
}
