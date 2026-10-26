package com.b2binventory;

import com.b2binventory.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAndEmployeeTest extends IntegrationTest {

    @Test
    void registerSeedsWorkspaceAndLoginWorks() {
        Session admin = registerBusiness("Seed Co");

        Response me = get(admin, "/api/auth/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.get("role").asText()).isEqualTo("ADMIN");
        assertThat(me.get("businessName").asText()).isEqualTo("Seed Co");
        assertThat(me.get("memberships").get(0).get("businessType").asText()).isEqualTo("Testing");

        Response login = post(null, "/api/auth/login", Map.of("email", admin.email(), "password", admin.password()));
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.get("accessToken").asText()).isNotBlank();

        Response badLogin = post(null, "/api/auth/login", Map.of("email", admin.email(), "password", "wrong-password"));
        assertThat(badLogin.status()).isEqualTo(401);
        assertThat(badLogin.get("code").asText()).isEqualTo("UNAUTHORIZED");

        Response settings = get(admin, "/api/business/settings");
        assertThat(settings.status()).isEqualTo(200);
        assertThat(settings.get("allowNegativeStock").asBoolean()).isFalse();
    }

    @Test
    void apiDocumentationIsServed() {
        Response docs = get(null, "/v3/api-docs");
        assertThat(docs.status()).isEqualTo(200);
        assertThat(docs.get("paths").has("/api/products")).isTrue();
    }

    @Test
    void requestsWithoutTokenAreRejected() {
        assertThat(get(null, "/api/business").status()).isEqualTo(401);
        Session bogus = new Session("not-a-jwt", 0, 0, "", "");
        assertThat(get(bogus, "/api/business").status()).isEqualTo(401);
    }

    @Test
    void validationErrorsAreReportedPerField() {
        Response r = post(null, "/api/auth/register", Map.of("businessName", "", "email", "nope", "password", "1"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.get("fieldErrors").has("businessName")).isTrue();
        assertThat(r.get("fieldErrors").has("email")).isTrue();
        assertThat(r.get("fieldErrors").has("password")).isTrue();
    }

    @Test
    void refreshTokenRotates() {
        String email = "rot-" + System.nanoTime() + "@test.local";
        Response reg = post(null, "/api/auth/register", Map.of("businessName", "Rotate", "fullName", "R",
                "email", email, "password", "password123"));
        String refresh = reg.get("refreshToken").asText();

        Response first = post(null, "/api/auth/refresh", Map.of("refreshToken", refresh));
        assertThat(first.status()).isEqualTo(200);
        // Reusing the old token fails and revokes the family.
        assertThat(post(null, "/api/auth/refresh", Map.of("refreshToken", refresh)).status()).isEqualTo(401);
        assertThat(post(null, "/api/auth/refresh", Map.of("refreshToken", first.get("refreshToken").asText())).status())
                .isEqualTo(401);
    }

    @Test
    void invitedStaffJoinsAndIsRestricted() {
        Session admin = registerBusiness("Team Co");
        Session staff = addEmployee(admin, "STAFF");

        Response me = get(staff, "/api/auth/me");
        assertThat(me.get("role").asText()).isEqualTo("STAFF");
        assertThat(me.get("businessId").asLong()).isEqualTo(admin.businessId());

        // Staff cannot administer employees or business settings.
        assertThat(get(staff, "/api/employees").status()).isEqualTo(403);
        assertThat(post(staff, "/api/invitations", Map.of("email", "x@test.local", "role", "STAFF")).status()).isEqualTo(403);
        assertThat(put(staff, "/api/business/settings", Map.of("defaultLowStockThreshold", 3)).status()).isEqualTo(403);

        Response list = get(admin, "/api/employees");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void permissionOverridesAndDeactivationTakeEffectImmediately() {
        Session admin = registerBusiness("Perm Co");
        Session staff = addEmployee(admin, "STAFF");

        assertThat(get(staff, "/api/employees").status()).isEqualTo(403);
        Response granted = put(admin, "/api/employees/" + staff.membershipId() + "/permissions",
                Map.of("overrides", Map.of("EMPLOYEE_MANAGE", true)));
        assertThat(granted.status()).isEqualTo(200);
        assertThat(get(staff, "/api/employees").status()).isEqualTo(200);

        Response deactivated = post(admin, "/api/employees/" + staff.membershipId() + "/deactivate", null);
        assertThat(deactivated.status()).isEqualTo(200);
        // Existing access token stops working at once.
        assertThat(get(staff, "/api/auth/me").status()).isEqualTo(401);
        assertThat(post(null, "/api/auth/login", Map.of("email", staff.email(), "password", staff.password())).status())
                .isEqualTo(401);
    }

    @Test
    void delegatedEmployeeManagerCannotEscalateToAdmin() {
        Session admin = registerBusiness("Escalate Co");
        Session manager = addEmployee(admin, "STAFF");
        Session other = addEmployee(admin, "STAFF");
        assertThat(put(admin, "/api/employees/" + manager.membershipId() + "/permissions",
                Map.of("overrides", Map.of("EMPLOYEE_MANAGE", true))).status()).isEqualTo(200);

        // Cannot promote themselves, change their own permissions, or create another admin.
        assertThat(put(manager, "/api/employees/" + manager.membershipId() + "/role", Map.of("role", "ADMIN")).status())
                .isEqualTo(403);
        assertThat(put(manager, "/api/employees/" + manager.membershipId() + "/permissions",
                Map.of("overrides", Map.of("BUSINESS_SETTINGS", true))).status()).isEqualTo(403);
        assertThat(put(manager, "/api/employees/" + other.membershipId() + "/role", Map.of("role", "ADMIN")).status())
                .isEqualTo(403);
        assertThat(post(manager, "/api/invitations", Map.of("email", "boss-" + System.nanoTime() + "@test.local",
                "role", "ADMIN")).status()).isEqualTo(403);
        // Cannot hand out permissions they do not hold, or touch the administrator.
        assertThat(put(manager, "/api/employees/" + other.membershipId() + "/permissions",
                Map.of("overrides", Map.of("BUSINESS_SETTINGS", true))).status()).isEqualTo(403);
        assertThat(put(manager, "/api/employees/" + admin.membershipId() + "/role", Map.of("role", "STAFF")).status())
                .isEqualTo(403);
        assertThat(post(manager, "/api/employees/" + admin.membershipId() + "/deactivate", null).status())
                .isEqualTo(403);

        // Ordinary delegated management still works.
        assertThat(put(manager, "/api/employees/" + other.membershipId() + "/role", Map.of("role", "INVENTORY_MANAGER"))
                .status()).isEqualTo(200);
        assertThat(get(admin, "/api/auth/me").get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void lastAdminCannotBeDemotedOrDeactivated() {
        Session admin = registerBusiness("Solo Co");
        assertThat(put(admin, "/api/employees/" + admin.membershipId() + "/role", Map.of("role", "STAFF")).status())
                .isEqualTo(409);
        assertThat(post(admin, "/api/employees/" + admin.membershipId() + "/deactivate", null).status())
                .isEqualTo(400);
    }

    @Test
    void employeesOfAnotherBusinessAreInvisible() {
        Session a = registerBusiness("Alpha");
        Session b = registerBusiness("Beta");
        assertThat(get(b, "/api/employees/" + a.membershipId()).status()).isEqualTo(404);
        assertThat(post(b, "/api/employees/" + a.membershipId() + "/deactivate", null).status()).isEqualTo(404);
        assertThat(get(a, "/api/auth/me").status()).isEqualTo(200);
    }
}
