package com.b2binventory.repository;

import com.b2binventory.entity.TradeReturn;
import com.b2binventory.entity.TradeReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeReturnRepository extends JpaRepository<TradeReturn, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TradeReturn r where r.id = ?1")
    Optional<TradeReturn> lock(Long id);

    List<TradeReturn> findByInvoiceIdOrderByRequestedAtAsc(Long invoiceId);

    Optional<TradeReturn> findByInvoiceIdAndClientRequestId(Long invoiceId, UUID clientRequestId);

    @Query("select count(r) from TradeReturn r where r.invoice.sellerBusinessId = ?1 and r.status = ?2")
    long countForSeller(Long sellerBusinessId, TradeReturnStatus status);
}
