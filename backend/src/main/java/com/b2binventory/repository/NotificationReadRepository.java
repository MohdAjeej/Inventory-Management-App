package com.b2binventory.repository;

import com.b2binventory.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, NotificationRead.Key> {

    List<NotificationRead> findByMembershipIdAndNotificationIdIn(Long membershipId, Collection<Long> notificationIds);
}
