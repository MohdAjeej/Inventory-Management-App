package com.b2binventory.repository;

import com.b2binventory.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    @Query("select u from AppUser u where lower(u.email) = lower(?1)")
    Optional<AppUser> findByEmailIgnoreCase(String email);
}
