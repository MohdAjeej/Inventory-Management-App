package com.b2binventory.service;

import com.b2binventory.dto.AuthDtos.*;
import com.b2binventory.entity.*;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.ForbiddenException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.exception.UnauthorizedException;
import com.b2binventory.repository.BusinessRepository;
import com.b2binventory.repository.InvitationRepository;
import com.b2binventory.repository.MembershipRepository;
import com.b2binventory.repository.UserRepository;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.RolePermissions;
import com.b2binventory.service.notification.NotificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository users;
    private final BusinessRepository businesses;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OnboardingService onboardingService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AuthService(UserRepository users, BusinessRepository businesses, MembershipRepository memberships,
                       InvitationRepository invitations, PasswordEncoder passwordEncoder, TokenService tokenService,
                       OnboardingService onboardingService, AuditService auditService,
                       NotificationService notificationService) {
        this.users = users;
        this.businesses = businesses;
        this.memberships = memberships;
        this.invitations = invitations;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.onboardingService = onboardingService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /**
     * Registers a new business with its first administrator. An existing user (same email) can create
     * an additional business by supplying their current password.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user != null) {
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new ConflictException("This email is already registered. Sign in, or use your existing password to add a new business.");
            }
        } else {
            user = new AppUser();
            user.setEmail(email);
            user.setFullName(request.fullName().trim());
            user.setPhone(blankToNull(request.phone()));
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            users.save(user);
        }

        Business business = new Business();
        business.setName(request.businessName().trim());
        business.setBusinessType(blankToNull(request.businessType()));
        business.setEmail(blankToNull(request.businessEmail()));
        business.setPhone(blankToNull(request.businessPhone()));
        business.setAddress(blankToNull(request.address()));
        business.setCity(blankToNull(request.city()));
        business.setState(blankToNull(request.state()));
        business.setCountry(blankToNull(request.country()));
        businesses.save(business);

        onboardingService.seed(business.getId(), request.timezone());

        Membership membership = new Membership();
        membership.setBusinessId(business.getId());
        membership.setUser(user);
        membership.setRole(Role.ADMIN);
        memberships.save(membership);

        auditService.record(business.getId(), membership.getId(), "BUSINESS_REGISTERED", "Business", business.getId(),
                Map.of("name", business.getName()));
        return respond(membership);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!user.isActive()) {
            throw new UnauthorizedException("Your account is disabled");
        }
        List<Membership> all = memberships.findByUserIdOrderByCreatedAtAsc(user.getId());
        Membership membership = all.stream()
                .filter(m -> request.businessId() == null || m.getBusinessId().equals(request.businessId()))
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new UnauthorizedException(all.isEmpty()
                        ? "You are not a member of any business"
                        : "Your access to this business has been deactivated"));
        return respond(membership);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(RefreshRequest request) {
        return respond(tokenService.consume(request.refreshToken()));
    }

    @Transactional
    public void logout(RefreshRequest request) {
        tokenService.revoke(request.refreshToken());
    }

    @Transactional
    public AuthResponse switchBusiness(SwitchBusinessRequest request) {
        CurrentUser me = CurrentUser.get();
        Membership target = memberships.findByUserIdAndBusinessId(me.userId(), request.businessId())
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("You are not an active member of that business"));
        return respond(memberships.findWithUserById(target.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public InvitationPreview previewInvitation(String code) {
        Invitation invitation = usableInvitation(code);
        Business business = businesses.findById(invitation.getBusinessId()).orElseThrow();
        boolean existing = users.findByEmailIgnoreCase(invitation.getEmail()).isPresent();
        return new InvitationPreview(business.getName(), invitation.getEmail(), invitation.getRole(), existing,
                invitation.getExpiresAt());
    }

    @Transactional
    public AuthResponse acceptInvitation(AcceptInvitationRequest request) {
        Invitation invitation = usableInvitation(request.code());
        AppUser user = users.findByEmailIgnoreCase(invitation.getEmail()).orElse(null);
        if (user != null) {
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new UnauthorizedException("An account with this email exists. Enter its current password to join.");
            }
        } else {
            if (request.fullName() == null || request.fullName().isBlank()) {
                throw new BadRequestException("Validation failed", Map.of("fullName", "Your name is required"));
            }
            user = new AppUser();
            user.setEmail(invitation.getEmail().toLowerCase());
            user.setFullName(request.fullName().trim());
            user.setPhone(blankToNull(request.phone()));
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            users.save(user);
        }
        if (memberships.findByUserIdAndBusinessId(user.getId(), invitation.getBusinessId()).isPresent()) {
            throw new ConflictException("You are already a member of this business");
        }

        Membership membership = new Membership();
        membership.setBusinessId(invitation.getBusinessId());
        membership.setUser(user);
        membership.setRole(invitation.getRole());
        memberships.save(membership);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());

        auditService.record(invitation.getBusinessId(), membership.getId(), "INVITATION_ACCEPTED", "Membership",
                membership.getId(), Map.of("email", user.getEmail(), "role", invitation.getRole()));
        notificationService.notify(invitation.getBusinessId(), null, NotificationService.EMPLOYEE_JOINED,
                user.getFullName() + " joined the team",
                user.getFullName() + " accepted the invitation as " + label(invitation.getRole()) + ".",
                Map.of("membershipId", membership.getId()), null, Duration.ZERO);
        return respond(membership);
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        return toMe(memberships.findWithUserById(CurrentUser.get().membershipId()).orElseThrow());
    }

    @Transactional
    public MeResponse updateProfile(UpdateProfileRequest request) {
        Membership membership = memberships.findWithUserById(CurrentUser.get().membershipId()).orElseThrow();
        membership.getUser().setFullName(request.fullName().trim());
        membership.getUser().setPhone(blankToNull(request.phone()));
        return toMe(membership);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        CurrentUser me = CurrentUser.get();
        AppUser user = users.findById(me.userId()).orElseThrow();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Validation failed", Map.of("currentPassword", "Current password is incorrect"));
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        memberships.findByUserIdOrderByCreatedAtAsc(user.getId()).forEach(m -> tokenService.revokeAll(m.getId()));
    }

    private Invitation usableInvitation(String code) {
        Invitation invitation = invitations.findByCode(code == null ? "" : code.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("Invitation not found. Check the code and try again."));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("This invitation has already been " + invitation.getStatus().name().toLowerCase());
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            throw new ConflictException("This invitation has expired. Ask your administrator for a new one.");
        }
        return invitation;
    }

    /** Issues tokens for a membership created elsewhere in the current transaction (backup restore). */
    @Transactional
    public AuthResponse signInAs(Long membershipId) {
        return respond(memberships.findWithUserById(membershipId).orElseThrow());
    }

    private AuthResponse respond(Membership membership) {
        TokenService.IssuedTokens tokens = tokenService.issue(membership);
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(), toMe(membership));
    }

    private MeResponse toMe(Membership membership) {
        AppUser user = membership.getUser();
        List<MembershipSummary> summaries = memberships.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .map(m -> {
                    Business b = businesses.findById(m.getBusinessId()).orElse(null);
                    return new MembershipSummary(m.getId(), m.getBusinessId(), b != null ? b.getName() : "", m.getRole(),
                            b != null ? b.getBusinessType() : null);
                })
                .toList();
        String businessName = businesses.findById(membership.getBusinessId()).map(Business::getName).orElse("");
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), membership.getId(),
                membership.getBusinessId(), businessName, membership.getRole(),
                RolePermissions.effective(membership.getRole(), membership.getPermissionOverrides()), summaries);
    }

    static String label(Role role) {
        return switch (role) {
            case ADMIN -> "Business Admin";
            case INVENTORY_MANAGER -> "Inventory Manager";
            case STAFF -> "Staff";
        };
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
