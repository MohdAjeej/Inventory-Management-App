package com.b2binventory.repository;

import com.b2binventory.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n where n.businessId = ?1 and (n.recipientId is null or n.recipientId = ?2) "
            + "and (?3 = false or not exists (select r from NotificationRead r where r.notificationId = n.id and r.membershipId = ?2)) "
            + "order by n.createdAt desc")
    Page<Notification> findVisible(Long businessId, Long membershipId, boolean unreadOnly, Pageable pageable);

    @Query("select count(n) from Notification n where n.businessId = ?1 and (n.recipientId is null or n.recipientId = ?2) "
            + "and not exists (select r from NotificationRead r where r.notificationId = n.id and r.membershipId = ?2)")
    long countUnread(Long businessId, Long membershipId);

    @Query("select n.id from Notification n where n.businessId = ?1 and (n.recipientId is null or n.recipientId = ?2) "
            + "and not exists (select r from NotificationRead r where r.notificationId = n.id and r.membershipId = ?2)")
    List<Long> unreadIds(Long businessId, Long membershipId);

    @Query("select n from Notification n where n.id = ?1 and n.businessId = ?2 and (n.recipientId is null or n.recipientId = ?3)")
    Optional<Notification> findVisibleById(Long id, Long businessId, Long membershipId);

    /** Used to avoid sending the same alert repeatedly. */
    boolean existsByBusinessIdAndDedupeKeyAndCreatedAtAfter(Long businessId, String dedupeKey, Instant after);
}
