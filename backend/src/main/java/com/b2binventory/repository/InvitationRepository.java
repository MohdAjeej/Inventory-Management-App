package com.b2binventory.repository;

import com.b2binventory.entity.Invitation;
import com.b2binventory.entity.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByCode(String code);

    Optional<Invitation> findByIdAndBusinessId(Long id, Long businessId);

    Page<Invitation> findByBusinessIdAndStatusOrderByCreatedAtDesc(Long businessId, InvitationStatus status, Pageable pageable);

    Page<Invitation> findByBusinessIdOrderByCreatedAtDesc(Long businessId, Pageable pageable);

    boolean existsByBusinessIdAndEmailIgnoreCaseAndStatus(Long businessId, String email, InvitationStatus status);
}
