package com.b2binventory.repository;

import com.b2binventory.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    /** q matches name, phone or email; archived ones only when asked. */
    @Query("select x from Customer x where x.businessId = ?1 and (?3 = true or x.archived = false) and "
            + "(?2 is null or lower(x.name) like ?2 or lower(coalesce(x.phone, '')) like ?2 "
            + "or lower(coalesce(x.email, '')) like ?2) order by lower(x.name)")
    List<Customer> search(Long businessId, String q, boolean includeArchived);
}
