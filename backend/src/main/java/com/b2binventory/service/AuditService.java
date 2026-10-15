package com.b2binventory.service;

import com.b2binventory.entity.AuditEvent;
import com.b2binventory.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** Joins the caller's transaction so audit rows are committed or rolled back together with the change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long businessId, Long actorMembershipId, String action, String entityType, Long entityId,
                       Map<String, ?> details) {
        AuditEvent event = new AuditEvent();
        event.setBusinessId(businessId);
        event.setActorId(actorMembershipId);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        if (details != null) {
            Map<String, Object> copy = new LinkedHashMap<>();
            details.forEach((k, v) -> {
                if (v != null) copy.put(k, v instanceof Enum<?> e ? e.name() : v);
            });
            event.setDetails(copy);
        }
        repository.save(event);
    }
}
