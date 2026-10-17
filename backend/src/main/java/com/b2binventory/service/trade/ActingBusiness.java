package com.b2binventory.service.trade;

import com.b2binventory.entity.Membership;
import com.b2binventory.entity.MembershipStatus;
import com.b2binventory.entity.Role;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.repository.MembershipRepository;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.RolePermissions;
import jakarta.persistence.EntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Runs part of a trade posting on behalf of the other business on the invoice, inside the same database
 * transaction. Example: when the buyer approves, the seller's stock and sale are recorded as the member who
 * sent the invoice. That member already authorised the sale by sending it; the existing stock and sales services
 * then apply the seller's own rules (business, warehouse, settings) unchanged.
 */
@Component
public class ActingBusiness {

    private final MembershipRepository memberships;
    private final EntityManager em;

    public ActingBusiness(MembershipRepository memberships, EntityManager em) {
        this.memberships = memberships;
        this.em = em;
    }

    /**
     * The member of businessId to act as: the preferred member (who sent or requested the document) while still
     * active and allowed to, otherwise the business's longest-standing active administrator.
     */
    public Long actor(Long businessId, Long preferredMembershipId, Permission needed) {
        if (preferredMembershipId != null) {
            Membership m = memberships.findWithUserById(preferredMembershipId).orElse(null);
            if (m != null && m.getBusinessId().equals(businessId) && m.isActive()
                    && RolePermissions.effective(m.getRole(), m.getPermissionOverrides()).contains(needed)) {
                return m.getId();
            }
        }
        List<Long> admins = em.createQuery("select m.id from Membership m where m.businessId = ?1 and m.status = ?2 "
                        + "and m.role = ?3 order by m.createdAt asc", Long.class)
                .setParameter(1, businessId).setParameter(2, MembershipStatus.ACTIVE).setParameter(3, Role.ADMIN)
                .setMaxResults(1).getResultList();
        if (admins.isEmpty()) throw new ConflictException("The other business has no active administrator");
        return admins.get(0);
    }

    public <T> T runAs(Long membershipId, Supplier<T> work) {
        Membership m = memberships.findWithUserById(membershipId)
                .orElseThrow(() -> new IllegalStateException("Membership " + membershipId + " not found"));
        Set<Permission> permissions = RolePermissions.effective(m.getRole(), m.getPermissionOverrides());
        CurrentUser principal = new CurrentUser(m.getUser().getId(), m.getId(), m.getBusinessId(), m.getUser().getEmail(),
                m.getUser().getFullName(), m.getRole(), permissions);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + m.getRole().name()));

        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext acting = SecurityContextHolder.createEmptyContext();
        acting.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        SecurityContextHolder.setContext(acting);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    public void runAs(Long membershipId, Runnable work) {
        runAs(membershipId, () -> {
            work.run();
            return null;
        });
    }
}
