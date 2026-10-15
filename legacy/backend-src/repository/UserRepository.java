package com.b2binventory.repository;

import com.b2binventory.domain.AppUser;
import com.b2binventory.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
    
    Optional<AppUser> findByEmail(String email);
    
    List<AppUser> findByBusinessId(Long businessId);
    
    List<AppUser> findByBusinessIdAndRole(Long businessId, Role role);
    
    boolean existsByEmail(String email);
}
