package com.b2binventory.repository;

import com.b2binventory.domain.Business;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository
        extends JpaRepository<Business, Long> {
}