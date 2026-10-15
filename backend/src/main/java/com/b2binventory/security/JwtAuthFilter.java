package com.b2binventory.security;

import com.b2binventory.entity.Membership;
import com.b2binventory.repository.MembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates the bearer token and reloads the membership on every request, so role changes and
 * deactivations take effect immediately rather than when the token expires.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MembershipRepository membershipRepository;

    public JwtAuthFilter(JwtService jwtService, MembershipRepository membershipRepository) {
        this.jwtService = jwtService;
        this.membershipRepository = membershipRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.parse(header.substring(7)).ifPresent(claims -> authenticate(claims, request));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(JwtService.TokenClaims claims, HttpServletRequest request) {
        membershipRepository.findWithUserById(claims.membershipId())
                .filter(m -> m.getBusinessId().equals(claims.businessId()))
                .filter(m -> m.getUser().getId().equals(claims.userId()))
                .filter(Membership::isActive)
                .ifPresent(m -> {
                    Set<Permission> permissions = RolePermissions.effective(m.getRole(), m.getPermissionOverrides());
                    CurrentUser principal = new CurrentUser(
                            m.getUser().getId(), m.getId(), m.getBusinessId(),
                            m.getUser().getEmail(), m.getUser().getFullName(), m.getRole(), permissions);
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + m.getRole().name()));
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute("businessId", m.getBusinessId());
                });
    }
}
