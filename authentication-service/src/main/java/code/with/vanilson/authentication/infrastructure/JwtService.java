package code.with.vanilson.authentication.infrastructure;

import code.with.vanilson.authentication.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JwtService — Infrastructure Layer (Security)
 * <p>
 * Handles all JWT lifecycle operations:
 * - Token generation (access token + refresh token)
 * - Token validation (signature + expiry)
 * - Claims extraction
 * <p>
 * Token claims include:
 * - jti      → unique token identifier (UUID, for revocation)
 * - sub      → user email (subject)
 * - userId   → database ID
 * - tenantId → SaaS tenant identifier (propagated by gateway as X-Tenant-ID)
 * - role     → user role (propagated as X-User-Role)
 * - tokenType → ACCESS or REFRESH
 * - iat      → issued-at timestamp
 * - exp      → expiry timestamp
 * <p>
 * Single Responsibility (SOLID-S): only JWT operations here.
 * Dependency Inversion (SOLID-D): depends on configurable @Value properties.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long      accessTokenExpiry;
    private final long      refreshTokenExpiry;
    private final MessageSource messageSource;

    public JwtService(
            @Value("${application.security.jwt.secret-key}")      String secretKey,
            @Value("${application.security.jwt.expiration}")       long accessTokenExpiry,
            @Value("${application.security.jwt.refresh-expiration}") long refreshTokenExpiry,
            MessageSource messageSource) {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        this.signingKey          = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiry   = accessTokenExpiry;
        this.refreshTokenExpiry  = refreshTokenExpiry;
        this.messageSource       = messageSource;
    }

    // -------------------------------------------------------
    // Token generation
    // -------------------------------------------------------

    /**
     * Generates an access token with tenantId and role claims.
     * These claims are read by the Gateway's JwtAuthenticationFilter
     * and forwarded to downstream services as X-Tenant-ID and X-User-Role.
     */
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId",    user.getId());
        claims.put("tenantId",  user.getTenantId());
        claims.put("role",      user.getRole().name());
        claims.put("tokenType", "ACCESS");

        String token = buildToken(claims, user.getEmail(), accessTokenExpiry);
        log.info(msg("auth.jwt.generated", user.getId(), user.getTenantId()));
        return token;
    }

    /**
     * Generates a refresh token with a tokenType claim for type-safe DB lookups.
     * Used by the /auth/refresh endpoint to issue new access tokens.
     */
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "REFRESH");
        return buildToken(claims, user.getEmail(), refreshTokenExpiry);
    }

    // -------------------------------------------------------
    // Token validation + extraction
    // -------------------------------------------------------

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String subject = extractSubject(token);
        return subject.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException ex) {
            // JJWT 0.12+ throws ExpiredJwtException during parsing when exp < now.
            // Treat that as "yes, expired" instead of propagating as a parse error.
            return true;
        }
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", String.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("tokenType", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiry) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiry))
                .signWith(signingKey)
                .compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
