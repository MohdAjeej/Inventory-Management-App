package com.b2binventory.service;

import com.b2binventory.domain.AppUser;
import com.b2binventory.domain.Business;
import com.b2binventory.domain.Role;
import com.b2binventory.dto.LoginRequest;
import com.b2binventory.dto.RegisterBusinessRequest;
import com.b2binventory.repository.BusinessRepository;
import com.b2binventory.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuthService {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(BusinessRepository businessRepository, 
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Map<String, Object> registerBusiness(RegisterBusinessRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered.");
        }

        Business business = new Business();
        business.setName(request.businessName());
        business.setType(request.businessType());
        business.setMobile(request.businessMobile());
        business.setEmail(request.businessEmail());
        business.setAddress(request.address());
        business.setCity(request.city());
        business.setState(request.state());
        business.setCountry(request.country());
        business.setGstNumber(request.gstNumber());
        businessRepository.save(business);

        AppUser user = new AppUser();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.ADMIN);
        user.setBusiness(business);
        userRepository.save(user);

        return Map.of(
            "message", "Business created successfully.",
            "businessId", business.getId(),
            "userId", user.getId()
        );
    }

    public Map<String, Object> login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        return Map.of(
            "message", "Login successful",
            "userId", user.getId(),
            "businessId", user.getBusiness().getId(),
            "name", user.getName(),
            "email", user.getEmail(),
            "role", user.getRole().name()
        );
    }
}
