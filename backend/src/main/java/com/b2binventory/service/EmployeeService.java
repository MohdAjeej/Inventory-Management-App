package com.b2binventory.service;

import com.b2binventory.config.AppProperties;
import com.b2binventory.dto.EmployeeDtos.*;
import com.b2binventory.dto.PageResponse;
import com.b2binventory.entity.*;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.ForbiddenException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.InvitationRepository;
import com.b2binventory.repository.MembershipRepository;
import com.b2binventory.repository.UserRepository;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.RolePermissions;
import com.b2binventory.service.notification.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class EmployeeService {

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final UserRepository users;
    private final TokenService tokenService;
    private final AuditService audit;
    private final NotificationService notifications;
    private final int invitationDays;

    public EmployeeService(MembershipRepository memberships, InvitationRepository invitations, UserRepository users,
                           TokenService tokenService, AuditService audit, NotificationService notifications,
                           AppProperties props) {
        this.memberships = memberships;
        this.invitations = invitations;
        this.users = users;
        this.tokenService = tokenService;
        this.audit = audit;
        this.notifications = notifications;
        this.invitationDays = props.invitations() != null && props.invitations().validDays() > 0
                ? props.invitations().validDays() : 7;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(String query, int page, int size) {
        CurrentUser me = CurrentUser.get();
        String q = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.of(memberships.search(me.businessId(), q,
                PageRequest.of(page, size, Sort.by("createdAt"))), EmployeeService::toResponse);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long membershipId) {
        return toResponse(find(membershipId));
    }

    @Transactional
    public EmployeeResponse changeRole(Long membershipId, Role role) {
        CurrentUser me = CurrentUser.get();
        Membership m = find(membershipId);
        requireCanManage(me, m);
        requireCanAssign(me, role);
        if (m.getRole() == Role.ADMIN && role != Role.ADMIN) {
            ensureAnotherActiveAdmin(m);
        }
        Role previous = m.getRole();
        m.setRole(role);
        audit.record(me.businessId(), me.membershipId(), "EMPLOYEE_ROLE_CHANGED", "Membership", m.getId(),
                Map.of("from", previous, "to", role));
        notifications.notify(me.businessId(), m.getId(), NotificationService.ROLE_CHANGED,
                "Your role was changed", "You are now " + AuthService.label(role) + ".",
                Map.of("role", role.name()), null, Duration.ZERO);
        return toResponse(m);
    }

    @Transactional
    public EmployeeResponse setPermissions(Long membershipId, Map<Permission, Boolean> overrides) {
        CurrentUser me = CurrentUser.get();
        Membership m = find(membershipId);
        requireCanManage(me, m);
        if (me.role() != Role.ADMIN) {
            // A delegated employee manager can only hand out permissions they hold themselves.
            // Grants an administrator already gave this employee may be kept unchanged.
            Map<Permission, Boolean> existing = m.getPermissionOverrides();
            overrides.forEach((p, granted) -> {
                if (Boolean.TRUE.equals(granted) && !Boolean.TRUE.equals(existing.get(p)) && !me.has(p)) {
                    throw new ForbiddenException("You cannot grant a permission you do not have: " + p.getLabel());
                }
            });
        }
        Set<Permission> defaults = RolePermissions.defaultsFor(m.getRole());
        m.getPermissionOverrides().clear();
        // Only keep overrides that actually differ from the role defaults.
        overrides.forEach((p, granted) -> {
            if (granted != null && granted != defaults.contains(p)) m.getPermissionOverrides().put(p, granted);
        });
        audit.record(me.businessId(), me.membershipId(), "EMPLOYEE_PERMISSIONS_CHANGED", "Membership", m.getId(),
                Map.of("overrides", m.getPermissionOverrides().toString()));
        return toResponse(m);
    }

    @Transactional
    public EmployeeResponse deactivate(Long membershipId) {
        CurrentUser me = CurrentUser.get();
        Membership m = find(membershipId);
        if (m.getId().equals(me.membershipId())) {
            throw new BadRequestException("You cannot deactivate your own account");
        }
        requireCanManage(me, m);
        if (m.getRole() == Role.ADMIN) {
            ensureAnotherActiveAdmin(m);
        }
        m.setStatus(MembershipStatus.DEACTIVATED);
        tokenService.revokeAll(m.getId());
        audit.record(me.businessId(), me.membershipId(), "EMPLOYEE_DEACTIVATED", "Membership", m.getId(),
                Map.of("email", m.getUser().getEmail()));
        notifications.notify(me.businessId(), null, NotificationService.EMPLOYEE_DEACTIVATED,
                m.getUser().getFullName() + " was deactivated",
                m.getUser().getFullName() + " no longer has access to this business.",
                Map.of("membershipId", m.getId()), null, Duration.ZERO);
        return toResponse(m);
    }

    @Transactional
    public EmployeeResponse reactivate(Long membershipId) {
        CurrentUser me = CurrentUser.get();
        Membership m = find(membershipId);
        requireCanManage(me, m);
        m.setStatus(MembershipStatus.ACTIVE);
        audit.record(me.businessId(), me.membershipId(), "EMPLOYEE_REACTIVATED", "Membership", m.getId(),
                Map.of("email", m.getUser().getEmail()));
        return toResponse(m);
    }

    @Transactional
    public InvitationResponse invite(InviteRequest request) {
        CurrentUser me = CurrentUser.get();
        requireCanAssign(me, request.role());
        String email = request.email().trim().toLowerCase();
        users.findByEmailIgnoreCase(email)
                .flatMap(u -> memberships.findByUserIdAndBusinessId(u.getId(), me.businessId()))
                .ifPresent(m -> {
                    throw new ConflictException("This person is already a member of the business");
                });
        if (invitations.existsByBusinessIdAndEmailIgnoreCaseAndStatus(me.businessId(), email, InvitationStatus.PENDING)) {
            throw new ConflictException("A pending invitation already exists for this email");
        }
        Invitation invitation = new Invitation();
        invitation.setBusinessId(me.businessId());
        invitation.setEmail(email);
        invitation.setRole(request.role());
        invitation.setCode(newCode());
        invitation.setInvitedBy(memberships.getReferenceById(me.membershipId()));
        invitation.setExpiresAt(Instant.now().plus(Duration.ofDays(invitationDays)));
        invitations.save(invitation);
        audit.record(me.businessId(), me.membershipId(), "EMPLOYEE_INVITED", "Invitation", invitation.getId(),
                Map.of("email", email, "role", request.role()));
        return toResponse(invitation, me.fullName());
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> invitations(InvitationStatus status, int page, int size) {
        CurrentUser me = CurrentUser.get();
        PageRequest pageable = PageRequest.of(page, size);
        var result = status == null
                ? invitations.findByBusinessIdOrderByCreatedAtDesc(me.businessId(), pageable)
                : invitations.findByBusinessIdAndStatusOrderByCreatedAtDesc(me.businessId(), status, pageable);
        return PageResponse.of(result, i -> toResponse(i,
                i.getInvitedBy() != null ? i.getInvitedBy().getUser().getFullName() : null));
    }

    @Transactional
    public void cancelInvitation(Long id) {
        CurrentUser me = CurrentUser.get();
        Invitation invitation = invitations.findByIdAndBusinessId(id, me.businessId())
                .orElseThrow(() -> new NotFoundException("Invitation", id));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("Only pending invitations can be cancelled");
        }
        invitation.setStatus(InvitationStatus.CANCELLED);
        audit.record(me.businessId(), me.membershipId(), "INVITATION_CANCELLED", "Invitation", id,
                Map.of("email", invitation.getEmail()));
    }

    public PermissionCatalog permissionCatalog() {
        List<PermissionInfo> all = Arrays.stream(Permission.values())
                .map(p -> new PermissionInfo(p, p.getLabel())).toList();
        Map<Role, Set<Permission>> defaults = new EnumMap<>(Role.class);
        for (Role role : Role.values()) defaults.put(role, RolePermissions.defaultsFor(role));
        return new PermissionCatalog(all, defaults);
    }

    private Membership find(Long membershipId) {
        return memberships.findByIdAndBusinessId(membershipId, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Employee", membershipId));
    }

    /**
     * EMPLOYEE_MANAGE can be granted to non-admins. Without these checks such a member could promote
     * themselves (or an invitee) to administrator, or demote and lock out the real administrators.
     */
    private static void requireCanManage(CurrentUser me, Membership target) {
        if (me.role() == Role.ADMIN) return;
        if (target.getId().equals(me.membershipId())) {
            throw new ForbiddenException("You cannot change your own role or permissions");
        }
        if (target.getRole() == Role.ADMIN) {
            throw new ForbiddenException("Only an administrator can manage another administrator");
        }
    }

    private static void requireCanAssign(CurrentUser me, Role role) {
        if (role == Role.ADMIN && me.role() != Role.ADMIN) {
            throw new ForbiddenException("Only an administrator can make someone an administrator");
        }
        if (me.role() == Role.ADMIN) return;
        // Assigning a role hands out its default permissions, so the same limit as for overrides applies.
        for (Permission p : RolePermissions.defaultsFor(role)) {
            if (!me.has(p)) {
                throw new ForbiddenException("You cannot assign a role with permissions you do not have: " + p.getLabel());
            }
        }
    }

    private void ensureAnotherActiveAdmin(Membership m) {
        if (m.getStatus() == MembershipStatus.ACTIVE
                && memberships.countByBusinessIdAndRoleAndStatus(m.getBusinessId(), Role.ADMIN, MembershipStatus.ACTIVE) <= 1) {
            throw new ConflictException("A business must keep at least one active administrator");
        }
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            String code = sb.toString();
            if (invitations.findByCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Could not generate a unique invitation code");
    }

    private static EmployeeResponse toResponse(Membership m) {
        AppUser u = m.getUser();
        return new EmployeeResponse(m.getId(), u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), m.getRole(),
                m.getStatus(), RolePermissions.effective(m.getRole(), m.getPermissionOverrides()),
                new LinkedHashMap<>(m.getPermissionOverrides()),
                m.getCreatedAt());
    }

    private static InvitationResponse toResponse(Invitation i, String invitedBy) {
        return new InvitationResponse(i.getId(), i.getEmail(), i.getRole(), i.getCode(), i.getStatus(), invitedBy,
                i.getExpiresAt(), i.getCreatedAt());
    }
}
