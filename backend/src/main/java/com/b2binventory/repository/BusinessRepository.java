package com.b2binventory.repository;

import com.b2binventory.domain.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    
    Optional<Business> findByEmail(String email);
    
    Optional<Business> findByGstNumber(String gstNumber);
}
