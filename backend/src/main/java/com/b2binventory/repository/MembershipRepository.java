package com.b2binventory.repository;

import com.b2binventory.entity.Membership;
import com.b2binventory.entity.MembershipStatus;
import com.b2binventory.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @EntityGraph(attributePaths = {"user", "permissionOverrides"})
    Optional<Membership> findWithUserById(Long id);

    @EntityGraph(attributePaths = {"user", "permissionOverrides"})
    Optional<Membership> findByIdAndBusinessId(Long id, Long businessId);

    @EntityGraph(attributePaths = {"user", "permissionOverrides"})
    List<Membership> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<Membership> findByUserIdAndBusinessId(Long userId, Long businessId);

    @EntityGraph(attributePaths = "user")
    @Query("select m from Membership m join m.user u where m.businessId = ?1 and "
            + "(?2 is null or lower(u.fullName) like lower(concat('%', cast(?2 as string), '%')) "
            + " or lower(u.email) like lower(concat('%', cast(?2 as string), '%')))")
    Page<Membership> search(Long businessId, String query, Pageable pageable);

    long countByBusinessIdAndRoleAndStatus(Long businessId, Role role, MembershipStatus status);

    @Query("select m.id from Membership m where m.businessId = ?1 and m.status = 'ACTIVE'")
    List<Long> activeIds(Long businessId);
}
