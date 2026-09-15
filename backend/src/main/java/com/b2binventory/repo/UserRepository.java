package com.b2binventory.repository;

import com.b2binventory.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);
}