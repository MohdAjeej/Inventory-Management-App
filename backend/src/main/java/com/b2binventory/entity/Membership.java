package com.b2binventory.entity;

import com.b2binventory.security.Permission;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "memberships")
@Getter
@Setter
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    /** Explicit grants (true) or revocations (false) layered on top of the role defaults. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "membership_permissions", joinColumns = @JoinColumn(name = "membership_id"))
    @MapKeyColumn(name = "permission")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "granted")
    private Map<Permission, Boolean> permissionOverrides = new HashMap<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long version;

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE && user.isActive();
    }
}
