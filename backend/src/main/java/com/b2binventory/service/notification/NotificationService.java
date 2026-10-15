package com.b2binventory.service.notification;

import com.b2binventory.dto.NotificationDtos.NotificationResponse;
import com.b2binventory.dto.NotificationDtos.UnreadCount;
import com.b2binventory.dto.PageResponse;
import com.b2binventory.entity.Notification;
import com.b2binventory.entity.NotificationRead;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.NotificationReadRepository;
import com.b2binventory.repository.NotificationRepository;
import com.b2binventory.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    public static final String LOW_STOCK = "LOW_STOCK";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String EMPLOYEE_JOINED = "EMPLOYEE_JOINED";
    public static final String EMPLOYEE_DEACTIVATED = "EMPLOYEE_DEACTIVATED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";

    private final NotificationRepository repository;
    private final NotificationReadRepository readRepository;
    private final List<NotificationChannel> channels;

    public NotificationService(NotificationRepository repository, NotificationReadRepository readRepository,
                               List<NotificationChannel> channels) {
        this.repository = repository;
        this.readRepository = readRepository;
        this.channels = channels;
    }

    /**
     * Creates a notification. When dedupeKey is set, an identical alert within the window is skipped,
     * so repeated stock movements do not flood users with the same low-stock warning.
     */
    @Transactional
    public void notify(Long businessId, Long recipientId, String type, String title, String body,
                       Map<String, Object> payload, String dedupeKey, Duration dedupeWindow) {
        if (dedupeKey != null && repository.existsByBusinessIdAndDedupeKeyAndCreatedAtAfter(
                businessId, dedupeKey, Instant.now().minus(dedupeWindow))) {
            return;
        }
        Notification n = new Notification();
        n.setBusinessId(businessId);
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        if (payload != null) n.getPayload().putAll(payload);
        n.setDedupeKey(dedupeKey);
        repository.save(n);
        channels.forEach(c -> c.deliver(n));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(boolean unreadOnly, int page, int size) {
        CurrentUser me = CurrentUser.get();
        Page<Notification> result = repository.findVisible(me.businessId(), me.membershipId(), unreadOnly,
                PageRequest.of(page, size));
        List<Long> ids = result.getContent().stream().map(Notification::getId).toList();
        Set<Long> read = ids.isEmpty() ? Set.of() : readRepository
                .findByMembershipIdAndNotificationIdIn(me.membershipId(), ids).stream()
                .map(NotificationRead::getNotificationId).collect(Collectors.toSet());
        return PageResponse.of(result, n -> new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getPayload(), read.contains(n.getId()), n.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount() {
        CurrentUser me = CurrentUser.get();
        return new UnreadCount(repository.countUnread(me.businessId(), me.membershipId()));
    }

    @Transactional
    public void markRead(Long id) {
        CurrentUser me = CurrentUser.get();
        repository.findVisibleById(id, me.businessId(), me.membershipId())
                .orElseThrow(() -> new NotFoundException("Notification", id));
        if (!readRepository.existsById(key(id, me.membershipId()))) {
            readRepository.save(new NotificationRead(id, me.membershipId()));
        }
    }

    @Transactional
    public void markAllRead() {
        CurrentUser me = CurrentUser.get();
        repository.unreadIds(me.businessId(), me.membershipId())
                .forEach(id -> readRepository.save(new NotificationRead(id, me.membershipId())));
    }

    private static NotificationRead.Key key(Long notificationId, Long membershipId) {
        NotificationRead.Key key = new NotificationRead.Key();
        key.setNotificationId(notificationId);
        key.setMembershipId(membershipId);
        return key;
    }
}
