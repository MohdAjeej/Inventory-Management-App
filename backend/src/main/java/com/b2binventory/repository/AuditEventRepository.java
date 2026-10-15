package com.b2binventory.repository;

import com.b2binventory.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByBusinessIdOrderByCreatedAtDesc(Long businessId, Pageable pageable);
}
