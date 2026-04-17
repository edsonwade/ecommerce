package code.with.vanilson.tenantcontext.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenValidatorTest {

    private static final String SECRET_B64 =
            Base64.getEncoder().encodeToString(new byte[64]); // 512-bit zero key for tests

    private final JwtTokenValidator validator = new JwtTokenValidator(SECRET_B64);

    private String token(Map<String, Object> claims, long ttlMs) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_B64));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("alice@example.com")
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(key)
                .compact();
    }

    @Test
    void validates_and_returns_claims() {
        String jwt = token(Map.of("userId", 42L, "tenantId", "t-1", "role", "ADMIN"), 60_000);
        JwtClaims claims = validator.validate(jwt);
        assertThat(claims.subject()).isEqualTo("alice@example.com");
        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.tenantId()).isEqualTo("t-1");
        assertThat(claims.role()).isEqualTo("ADMIN");
    }

    @Test
    void rejects_expired_token() {
        String jwt = token(Map.of("userId", 1L, "tenantId", "t", "role", "USER"), -1_000);
        assertThatThrownBy(() -> validator.validate(jwt))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejects_bad_signature() {
        String jwt = token(Map.of("userId", 1L, "tenantId", "t", "role", "USER"), 60_000);
        String tampered = jwt.substring(0, jwt.length() - 4) + "AAAA";
        assertThatThrownBy(() -> validator.validate(tampered))
                .isInstanceOf(InvalidJwtException.class);
    }
}
