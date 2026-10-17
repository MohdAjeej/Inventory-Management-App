package com.b2binventory.repository;

import com.b2binventory.entity.BusinessConnection;
import com.b2binventory.entity.ConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BusinessConnectionRepository extends JpaRepository<BusinessConnection, Long> {

    @Query("select c from BusinessConnection c where c.id = ?1 and (c.requesterBusinessId = ?2 or c.addresseeBusinessId = ?2)")
    Optional<BusinessConnection> findForBusiness(Long id, Long businessId);

    @Query("select c from BusinessConnection c where c.status in ?3 and ((c.requesterBusinessId = ?1 and c.addresseeBusinessId = ?2) "
            + "or (c.requesterBusinessId = ?2 and c.addresseeBusinessId = ?1))")
    Optional<BusinessConnection> findBetween(Long a, Long b, Collection<ConnectionStatus> statuses);

    @Query("select c from BusinessConnection c where c.status in ?2 and (c.requesterBusinessId = ?1 or c.addresseeBusinessId = ?1) "
            + "order by c.createdAt desc")
    List<BusinessConnection> findForBusiness(Long businessId, Collection<ConnectionStatus> statuses);

    @Query("select count(c) from BusinessConnection c where c.addresseeBusinessId = ?1 and c.status = 'PENDING'")
    long countIncoming(Long businessId);
}
