package com.b2binventory.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

/** Per-business counters (invoice, purchase and return numbers), incremented under a row lock. */
@Repository
public class SequenceRepository {

    private final EntityManager em;

    public SequenceRepository(EntityManager em) {
        this.em = em;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(Long businessId, String name) {
        em.createNativeQuery("INSERT INTO business_sequences (business_id, name, next_value) VALUES (?1, ?2, 1) "
                        + "ON CONFLICT DO NOTHING")
                .setParameter(1, businessId).setParameter(2, name).executeUpdate();
        Number value = (Number) em.createNativeQuery("UPDATE business_sequences SET next_value = next_value + 1 "
                        + "WHERE business_id = ?1 AND name = ?2 RETURNING next_value - 1")
                .setParameter(1, businessId).setParameter(2, name).getSingleResult();
        return value.longValue();
    }
}
