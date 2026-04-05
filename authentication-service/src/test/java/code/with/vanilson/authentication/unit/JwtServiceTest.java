package code.with.vanilson.authentication.unit;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.infrastructure.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JwtServiceTest — Pure unit tests for JWT generation, validation, and claim extraction.
 * No Spring context — JwtService is constructed directly.
 */
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    // 64-byte key (HS512): Base64("testSecretKeyForTestingOnlyNotForProductionUsage0000000000000000")
    private static final String TEST_SECRET =
            "dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA==";
    private static final long ACCESS_EXPIRY  = 900_000L;   // 15 min
    private static final long REFRESH_EXPIRY = 86_400_000L; // 24 h

    private JwtService jwtService;
    private User       testUser;

    @BeforeEach
    void setUp() {
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(any(), any(), any())).thenReturn("test message");

        jwtService = new JwtService(TEST_SECRET, ACCESS_EXPIRY, REFRESH_EXPIRY, messageSource);

        testUser = User.builder()
                .id(42L)
                .firstname("Jane")
                .lastname("Doe")
                .email("jane.doe@example.com")
                .password("$2a$12$hashed")
                .role(Role.USER)
                .tenantId("tenant-abc")
                .accountEnabled(true)
                .accountLocked(false)
                .build();
    }

    // -------------------------------------------------------
    // Access token generation
    // -------------------------------------------------------
    @Nested
    @DisplayName("Access Token Generation")
    class AccessTokenGeneration {

        @Test
        @DisplayName("generates a non-blank access token")
        void generatesNonBlankAccessToken() {
            String token = jwtService.generateAccessToken(testUser);
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("access token subject equals user email")
        void accessTokenSubjectIsEmail() {
            String token = jwtService.generateAccessToken(testUser);
            assertThat(jwtService.extractSubject(token)).isEqualTo("jane.doe@example.com");
        }

        @Test
        @DisplayName("access token contains userId claim")
        void accessTokenContainsUserId() {
            String token = jwtService.generateAccessToken(testUser);
            assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        }

        @Test
        @DisplayName("access token contains tenantId claim")
        void accessTokenContainsTenantId() {
            String token = jwtService.generateAccessToken(testUser);
            assertThat(jwtService.extractTenantId(token)).isEqualTo("tenant-abc");
        }

        @Test
        @DisplayName("access token contains role claim")
        void accessTokenContainsRole() {
            String token = jwtService.generateAccessToken(testUser);
            assertThat(jwtService.extractRole(token)).isEqualTo("USER");
        }

        @Test
        @DisplayName("access token tokenType claim is ACCESS")
        void accessTokenTypeIsAccess() {
            String token = jwtService.generateAccessToken(testUser);
            assertThat(jwtService.extractTokenType(token)).isEqualTo("ACCESS");
        }

        @Test
        @DisplayName("access token has a unique JTI (UUID)")
        void accessTokenHasUniqueJti() {
            String token1 = jwtService.generateAccessToken(testUser);
            String token2 = jwtService.generateAccessToken(testUser);
            assertThat(jwtService.extractJti(token1))
                    .isNotBlank()
                    .isNotEqualTo(jwtService.extractJti(token2));
        }

        @Test
        @DisplayName("two consecutive access tokens differ (different JTIs)")
        void consecutiveAccessTokensDiffer() {
            String t1 = jwtService.generateAccessToken(testUser);
            String t2 = jwtService.generateAccessToken(testUser);
            assertThat(t1).isNotEqualTo(t2);
        }
    }

    // -------------------------------------------------------
    // Refresh token generation
    // -------------------------------------------------------
    @Nested
    @DisplayName("Refresh Token Generation")
    class RefreshTokenGeneration {

        @Test
        @DisplayName("generates a non-blank refresh token")
        void generatesNonBlankRefreshToken() {
            String token = jwtService.generateRefreshToken(testUser);
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("refresh token tokenType claim is REFRESH")
        void refreshTokenTypeIsRefresh() {
            String token = jwtService.generateRefreshToken(testUser);
            assertThat(jwtService.extractTokenType(token)).isEqualTo("REFRESH");
        }

        @Test
        @DisplayName("refresh token subject equals user email")
        void refreshTokenSubjectIsEmail() {
            String token = jwtService.generateRefreshToken(testUser);
            assertThat(jwtService.extractSubject(token)).isEqualTo("jane.doe@example.com");
        }

        @Test
        @DisplayName("refresh token has a unique JTI")
        void refreshTokenHasUniqueJti() {
            String t1 = jwtService.generateRefreshToken(testUser);
            String t2 = jwtService.generateRefreshToken(testUser);
            assertThat(jwtService.extractJti(t1))
                    .isNotBlank()
                    .isNotEqualTo(jwtService.extractJti(t2));
        }

        @Test
        @DisplayName("access and refresh tokens have different JTIs")
        void accessAndRefreshHaveDifferentJtis() {
            String access  = jwtService.generateAccessToken(testUser);
            String refresh = jwtService.generateRefreshToken(testUser);
            assertThat(jwtService.extractJti(access))
                    .isNotEqualTo(jwtService.extractJti(refresh));
        }
    }

    // -------------------------------------------------------
    // Token validation
    // -------------------------------------------------------
    @Nested
    @DisplayName("Token Validation")
    class TokenValidation {

        @Test
        @DisplayName("valid access token is accepted for correct user")
        void validTokenAcceptedForCorrectUser() {
            String token = jwtService.generateAccessToken(testUser);
            UserDetails details = buildUserDetails("jane.doe@example.com");
            assertThat(jwtService.isTokenValid(token, details)).isTrue();
        }

        @Test
        @DisplayName("valid token is rejected for wrong subject")
        void validTokenRejectedForWrongUser() {
            String token = jwtService.generateAccessToken(testUser);
            UserDetails other = buildUserDetails("other@example.com");
            assertThat(jwtService.isTokenValid(token, other)).isFalse();
        }

        @Test
        @DisplayName("expired token is detected correctly")
        void expiredTokenDetected() throws Exception {
            // Issue a token that expires in 1 ms
            JwtService shortLived = new JwtService(TEST_SECRET, 1L, REFRESH_EXPIRY,
                    mock(MessageSource.class));
            String token = shortLived.generateAccessToken(testUser);
            Thread.sleep(10); // wait for expiry
            assertThat(shortLived.isTokenExpired(token)).isTrue();
        }

        @Test
        @DisplayName("non-expired token is not flagged as expired")
        void nonExpiredTokenNotFlagged() {
            String token = jwtService.generateAccessToken(testUser);
            assertThat(jwtService.isTokenExpired(token)).isFalse();
        }

        @Test
        @DisplayName("malformed token throws JwtException on extraction")
        void malformedTokenThrowsOnExtraction() {
            assertThatThrownBy(() -> jwtService.extractSubject("not.a.jwt"))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("token signed with different key throws JwtException")
        void tokenWithWrongKeyRejected() throws Exception {
            // Different secret key
            String otherSecret =
                    "b3RoZXJTZWNyZXRLZXlGb3JUZXN0aW5nT25seU5vdEZvclByb2R1Y3Rpb25Vc2FnZTAwMDAwMDAwMDA=";
            JwtService other = new JwtService(otherSecret, ACCESS_EXPIRY, REFRESH_EXPIRY,
                    mock(MessageSource.class));
            String foreignToken = other.generateAccessToken(testUser);

            assertThatThrownBy(() -> jwtService.extractSubject(foreignToken))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("tampered token payload throws JwtException")
        void tamperedTokenRejected() {
            String token = jwtService.generateAccessToken(testUser);
            // Modify the payload part (index 1 in dot-separated JWT)
            String[] parts = token.split("\\.");
            String tampered = parts[0] + ".dGFtcGVyZWQ=" + "." + parts[2];

            assertThatThrownBy(() -> jwtService.extractSubject(tampered))
                    .isInstanceOf(JwtException.class);
        }
    }

    // -------------------------------------------------------
    // Claim extraction edge cases
    // -------------------------------------------------------
    @Nested
    @DisplayName("Claim Extraction")
    class ClaimExtraction {

        @Test
        @DisplayName("extractJti returns a valid UUID string")
        void extractJtiReturnsUuid() {
            String token = jwtService.generateAccessToken(testUser);
            String jti = jwtService.extractJti(token);
            assertThat(jti).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("ADMIN role is propagated correctly in token claims")
        void adminRolePropagatedInToken() {
            User admin = User.builder()
                    .id(1L).email("admin@test.com").password("pw")
                    .role(Role.ADMIN).tenantId("default")
                    .accountEnabled(true).accountLocked(false).build();
            String token = jwtService.generateAccessToken(admin);
            assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("SELLER role is propagated correctly in token claims")
        void sellerRolePropagatedInToken() {
            User seller = User.builder()
                    .id(2L).email("seller@test.com").password("pw")
                    .role(Role.SELLER).tenantId("tenant-x")
                    .accountEnabled(true).accountLocked(false).build();
            String token = jwtService.generateAccessToken(seller);
            assertThat(jwtService.extractRole(token)).isEqualTo("SELLER");
        }
    }

    // -------------------------------------------------------
    // Helper
    // -------------------------------------------------------
    private UserDetails buildUserDetails(String email) {
        return new org.springframework.security.core.userdetails.User(
                email, "pw", java.util.List.of());
    }
}
