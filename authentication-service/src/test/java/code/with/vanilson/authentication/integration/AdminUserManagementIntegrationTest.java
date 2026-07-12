package code.with.vanilson.authentication.integration;

import code.with.vanilson.authentication.infrastructure.CustomerRegistrationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminUserManagementIntegrationTest — admin user CRUD against a real PostgreSQL
 * (Testcontainers + Flyway) with the full security filter chain.
 * <p>
 * The platform admin used as the actor is the one seeded by {@code AdminBootstrapRunner}
 * (defaults: admin@obsidian.com / Admin@123!) — exactly what production relies on.
 * </p>
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "spring.config.import=optional:configserver:"
})
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Admin User Management Integration Tests (Testcontainers + MockMvc)")
class AdminUserManagementIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("auth_admin_test")
                    .withUsername("auth_user")
                    .withPassword("auth_pass");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    // Mock the customer-service Feign client — no customer-service in integration tests.
    @MockBean
    @SuppressWarnings("unused")
    CustomerRegistrationClient customerRegistrationClient;

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String BASE        = "/api/v1/auth";
    private static final String USERS       = BASE + "/users";
    private static final String ADMIN_EMAIL = "admin@obsidian.com";
    private static final String ADMIN_PASS  = "Admin@123!";

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------
    private String json(Object... pairs) throws Exception {
        var map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) map.put(pairs[i], pairs[i + 1]);
        return objectMapper.writeValueAsString(map);
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String uniqueEmail() {
        return "admin_created_" + System.nanoTime() + "@example.com";
    }

    private String loginToken(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return bodyOf(login).get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return loginToken(ADMIN_EMAIL, ADMIN_PASS);
    }

    /** Creates a user via the admin endpoint and returns its id. */
    private long createUserAs(String token, String email, String password, String role) throws Exception {
        MvcResult created = mockMvc.perform(post(USERS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("firstname", "Managed", "lastname", "User",
                                "email", email, "password", password, "role", role)))
                .andExpect(status().isCreated())
                .andReturn();
        return bodyOf(created).get("id").asLong();
    }

    // -------------------------------------------------------
    // POST /api/v1/auth/users
    // -------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auth/users — admin creates users")
    class CreateUser {

        @Test
        @DisplayName("201 Created: admin creates a SELLER who can then log in")
        void adminCreatesSellerWhoCanLogin() throws Exception {
            String email = uniqueEmail();

            mockMvc.perform(post(USERS)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "Sella", "lastname", "Vendor",
                                    "email", email, "password", "SellerPass1!",
                                    "role", "SELLER")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id",             notNullValue()))
                    .andExpect(jsonPath("$.email",          is(email)))
                    .andExpect(jsonPath("$.role",           is("SELLER")))
                    .andExpect(jsonPath("$.tenantId",       is("default")))
                    .andExpect(jsonPath("$.accountEnabled", is(true)))
                    // Admin-created sellers skip the approval queue.
                    .andExpect(jsonPath("$.sellerStatus",   is("APPROVED")));

            // The created seller can authenticate with the admin-assigned password.
            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "SellerPass1!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role", is("SELLER")))
                    .andExpect(jsonPath("$.sellerStatus", is("APPROVED")));
        }

        @Test
        @DisplayName("self-registered SELLER is born PENDING_APPROVAL (register + login carry it)")
        void selfRegisteredSellerIsPendingApproval() throws Exception {
            String email = uniqueEmail();

            mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "Self", "lastname", "Seller",
                                    "email", email, "password", "SellerPass1!",
                                    "role", "SELLER")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role",         is("SELLER")))
                    .andExpect(jsonPath("$.sellerStatus", is("PENDING_APPROVAL")));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "SellerPass1!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerStatus", is("PENDING_APPROVAL")));
        }

        @Test
        @DisplayName("non-seller responses carry no sellerStatus (field absent, not null)")
        void nonSellerHasNoSellerStatus() throws Exception {
            String email = uniqueEmail();

            mockMvc.perform(post(USERS)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "Plain", "lastname", "User",
                                    "email", email, "password", "Password1!",
                                    "role", "USER")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sellerStatus").value(org.hamcrest.Matchers.nullValue()));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "Password1!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerStatus").doesNotExist());
        }

        @Test
        @DisplayName("201 Created: admin creates another ADMIN (public register blocks this)")
        void adminCreatesAnotherAdmin() throws Exception {
            String email = uniqueEmail();

            mockMvc.perform(post(USERS)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "Second", "lastname", "Admin",
                                    "email", email, "password", "AdminPass1!",
                                    "role", "ADMIN")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role", is("ADMIN")));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "AdminPass1!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role", is("ADMIN")));
        }

        @Test
        @DisplayName("409 Conflict: duplicate email rejected")
        void duplicateEmailReturns409() throws Exception {
            String email = uniqueEmail();
            String payload = json("firstname", "Dup", "lastname", "User",
                    "email", email, "password", "Password1!", "role", "USER");

            mockMvc.perform(post(USERS)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(USERS)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode", is("auth.user.already.exists")));
        }

        @Test
        @DisplayName("403 Forbidden: a regular USER cannot create users")
        void regularUserGets403() throws Exception {
            String email = uniqueEmail();
            mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "Reg", "lastname", "Ular",
                                    "email", email, "password", "Password1!")))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(USERS)
                            .header("Authorization", "Bearer " + loginToken(email, "Password1!"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "New", "lastname", "User",
                                    "email", uniqueEmail(), "password", "Password1!",
                                    "role", "USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("401 Unauthorized: anonymous request rejected")
        void anonymousGets401() throws Exception {
            mockMvc.perform(post(USERS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "New", "lastname", "User",
                                    "email", uniqueEmail(), "password", "Password1!",
                                    "role", "USER")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("400 Bad Request: missing role rejected with field error")
        void missingRoleReturns400() throws Exception {
            mockMvc.perform(post(USERS)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "New", "lastname", "User",
                                    "email", uniqueEmail(), "password", "Password1!")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.role", notNullValue()));
        }

        @Test
        @DisplayName("400 Bad Request: invalid role value rejected (not 500)")
        void invalidRoleValueReturns400() throws Exception {
            mockMvc.perform(post(USERS)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "New", "lastname", "User",
                                    "email", uniqueEmail(), "password", "Password1!",
                                    "role", "SUPERADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.bad.request")));
        }

        @Test
        @DisplayName("public /register still blocks ADMIN self-registration (unchanged behaviour)")
        void publicRegisterStillBlocksAdmin() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "Evil", "lastname", "Actor",
                                    "email", uniqueEmail(), "password", "Password1!",
                                    "role", "ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.register.admin.denied")));
        }
    }

    // -------------------------------------------------------
    // PATCH /api/v1/auth/users/{userId}
    // -------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/auth/users/{userId} — admin edits users")
    class UpdateUser {

        @Test
        @DisplayName("200 OK: admin renames a user; profile fields reflected in response")
        void adminRenamesUser() throws Exception {
            String email = uniqueEmail();
            long id = createUserAs(adminToken(), email, "Password1!", "USER");

            mockMvc.perform(patch(USERS + "/" + id)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "Renamed", "lastname", "ByAdmin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstname", is("Renamed")))
                    .andExpect(jsonPath("$.lastname",  is("ByAdmin")))
                    .andExpect(jsonPath("$.email",     is(email)));
        }

        @Test
        @DisplayName("email change: user logs in with the NEW email, old email is dead")
        void adminChangesEmail() throws Exception {
            String oldEmail = uniqueEmail();
            String newEmail = uniqueEmail();
            long id = createUserAs(adminToken(), oldEmail, "Password1!", "USER");

            mockMvc.perform(patch(USERS + "/" + id)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", newEmail)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email", is(newEmail)));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", newEmail, "password", "Password1!")))
                    .andExpect(status().isOk());

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", oldEmail, "password", "Password1!")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("409 Conflict: email already owned by another account")
        void emailTakenReturns409() throws Exception {
            String emailA = uniqueEmail();
            String emailB = uniqueEmail();
            createUserAs(adminToken(), emailA, "Password1!", "USER");
            long idB = createUserAs(adminToken(), emailB, "Password1!", "USER");

            mockMvc.perform(patch(USERS + "/" + idB)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", emailA)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode", is("auth.account.email.taken")));
        }

        @Test
        @DisplayName("400 Bad Request: empty body rejected")
        void emptyBodyReturns400() throws Exception {
            long id = createUserAs(adminToken(), uniqueEmail(), "Password1!", "USER");

            mockMvc.perform(patch(USERS + "/" + id)
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------
    // PATCH /api/v1/auth/users/{userId}/status
    // -------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/auth/users/{userId}/status — deactivate/reactivate")
    class UpdateUserStatus {

        @Test
        @DisplayName("deactivated user cannot log in; reactivated user can again")
        void deactivateBlocksLoginReactivateRestoresIt() throws Exception {
            String email = uniqueEmail();
            long id = createUserAs(adminToken(), email, "Password1!", "USER");

            // Deactivate → login denied (disabled accounts fail authentication)
            mockMvc.perform(patch(USERS + "/" + id + "/status")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("enabled", false)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountEnabled", is(false)));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "Password1!")))
                    .andExpect(status().isUnauthorized());

            // Reactivate → login works again
            mockMvc.perform(patch(USERS + "/" + id + "/status")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("enabled", true)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountEnabled", is(true)));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "Password1!")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("deactivation revokes sessions — the old access token stops working")
        void deactivationRevokesExistingSessions() throws Exception {
            String email = uniqueEmail();
            long id = createUserAs(adminToken(), email, "Password1!", "USER");
            String victimToken = loginToken(email, "Password1!");

            mockMvc.perform(patch(USERS + "/" + id + "/status")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("enabled", false)))
                    .andExpect(status().isOk());

            // Any authenticated call with the revoked token must now be rejected.
            mockMvc.perform(post(BASE + "/logout")
                            .header("Authorization", "Bearer " + victimToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("400 Bad Request: admin cannot deactivate self")
        void adminCannotDeactivateSelf() throws Exception {
            MvcResult login = mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", ADMIN_EMAIL, "password", ADMIN_PASS)))
                    .andExpect(status().isOk())
                    .andReturn();
            long adminId = bodyOf(login).get("userId").asLong();

            mockMvc.perform(patch(USERS + "/" + adminId + "/status")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("enabled", false)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.admin.self.deactivate.denied")));
        }
    }

    // -------------------------------------------------------
    // PATCH /api/v1/auth/users/{userId}/seller-status
    // -------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/auth/users/{userId}/seller-status — approve/suspend sellers")
    class UpdateSellerStatus {

        @Test
        @DisplayName("admin approves a self-registered (pending) seller; next login carries APPROVED")
        void adminApprovesPendingSeller() throws Exception {
            String email = uniqueEmail();

            MvcResult registered = mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "Pending", "lastname", "Seller",
                                    "email", email, "password", "SellerPass1!",
                                    "role", "SELLER")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sellerStatus", is("PENDING_APPROVAL")))
                    .andReturn();
            long sellerId = bodyOf(registered).get("userId").asLong();

            mockMvc.perform(patch(USERS + "/" + sellerId + "/seller-status")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("status", "APPROVED")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerStatus", is("APPROVED")));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "SellerPass1!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerStatus", is("APPROVED")));
        }

        @Test
        @DisplayName("suspension revokes sessions — the seller's refresh token dies (401)")
        void suspensionRevokesRefreshToken() throws Exception {
            String email = uniqueEmail();
            long sellerId = createUserAs(adminToken(), email, "SellerPass1!", "SELLER");

            MvcResult login = mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "SellerPass1!")))
                    .andExpect(status().isOk())
                    .andReturn();
            String refreshToken = bodyOf(login).get("refreshToken").asText();

            mockMvc.perform(patch(USERS + "/" + sellerId + "/seller-status")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("status", "SUSPENDED")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerStatus", is("SUSPENDED")));

            // The pre-suspension refresh token must be dead.
            mockMvc.perform(post(BASE + "/refresh")
                            .header("Authorization", "Bearer " + refreshToken))
                    .andExpect(status().isUnauthorized());

            // A fresh login still works (suspension blocks writes, not authentication)
            // and surfaces the SUSPENDED status for the frontend banner.
            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "SellerPass1!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerStatus", is("SUSPENDED")));
        }

        @Test
        @DisplayName("400 Bad Request: seller-status on a non-SELLER target")
        void nonSellerTargetReturns400() throws Exception {
            long userId = createUserAs(adminToken(), uniqueEmail(), "Password1!", "USER");

            mockMvc.perform(patch(USERS + "/" + userId + "/seller-status")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("status", "APPROVED")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.seller.status.not.seller")));
        }

        @Test
        @DisplayName("404 Not Found: seller-status on a non-existent user")
        void missingUserReturns404() throws Exception {
            mockMvc.perform(patch(USERS + "/999999/seller-status")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("status", "APPROVED")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode", is("auth.user.not.found")));
        }
    }

    // -------------------------------------------------------
    // DELETE /api/v1/auth/users/{userId}
    // -------------------------------------------------------
    @Nested
    @DisplayName("DELETE /api/v1/auth/users/{userId} — admin soft-delete")
    class DeleteUser {

        @Test
        @DisplayName("deleted user cannot log in and the email is freed for re-registration")
        void deleteAnonymizesAndFreesEmail() throws Exception {
            String email = uniqueEmail();
            long id = createUserAs(adminToken(), email, "Password1!", "USER");

            mockMvc.perform(delete(USERS + "/" + id)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email, "password", "Password1!")))
                    .andExpect(status().isUnauthorized());

            // Soft delete + anonymize frees the address — a new account may claim it.
            mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("firstname", "Fresh", "lastname", "Start",
                                    "email", email, "password", "Password1!")))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("400 Bad Request: admin cannot delete self")
        void adminCannotDeleteSelf() throws Exception {
            MvcResult login = mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", ADMIN_EMAIL, "password", ADMIN_PASS)))
                    .andExpect(status().isOk())
                    .andReturn();
            long adminId = bodyOf(login).get("userId").asLong();

            mockMvc.perform(delete(USERS + "/" + adminId)
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("auth.admin.self.delete.denied")));
        }

        @Test
        @DisplayName("404 Not Found: deleting a non-existent user")
        void deleteMissingUserReturns404() throws Exception {
            mockMvc.perform(delete(USERS + "/999999")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode", is("auth.user.not.found")));
        }
    }
}
