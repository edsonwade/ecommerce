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
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
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
 * Signing algorithm: RS256 (asymmetric RSA) — private key signs, public key verifies.
 * The private key is held exclusively by auth-service; the gateway only needs the public key.
 * Set JWT_PRIVATE_KEY env var to a base64-encoded PKCS8 RSA private key PEM.
 * Fallback: if JWT_PRIVATE_KEY is absent, falls back to HS256 using JWT_SECRET (legacy support).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class JwtService {

    private final Object signingKey;   // RSAPrivateKey (RS256) or SecretKey (HS256 fallback)
    private final boolean useRsa;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;
    private final MessageSource messageSource;

    public JwtService(
            @Value("${application.security.jwt.private-key:}") String privateKeyBase64,
            @Value("${application.security.jwt.secret-key:}") String secretKeyBase64,
            @Value("${application.security.jwt.expiration}") long accessTokenExpiry,
            @Value("${application.security.jwt.refresh-expiration}") long refreshTokenExpiry,
            MessageSource messageSource) {

        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
        this.messageSource = messageSource;

        if (privateKeyBase64 != null && !privateKeyBase64.isBlank()) {
            this.signingKey = loadRsaPrivateKey(privateKeyBase64);
            this.useRsa = true;
            log.info("[JwtService] Initialized with RS256 asymmetric signing");
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
            this.useRsa = false;
            log.warn("[JwtService] Falling back to HS256 — set JWT_PRIVATE_KEY for RS256");
        }
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
        claims.put("userId", user.getId());
        claims.put("tenantId", user.getTenantId());
        claims.put("role", user.getRole().name());
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
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiry));

        if (useRsa) {
            builder.signWith((RSAPrivateKey) signingKey, Jwts.SIG.RS256);
        } else {
            builder.signWith((SecretKey) signingKey);
        }
        return builder.compact();
    }

    private Claims extractAllClaims(String token) {
        var parserBuilder = Jwts.parser();
        if (useRsa) {
            // For RS256 verification on the auth-service side (e.g. refresh token validation),
            // we need the public key. However, extractAllClaims is only called internally on
            // tokens we just issued — use the private key's associated public key.
            parserBuilder.verifyWith((SecretKey) ((RSAPrivateKey) signingKey).getClass()
                    .cast(signingKey));
        } else {
            parserBuilder.verifyWith((SecretKey) signingKey);
        }
        return parserBuilder.build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private static RSAPrivateKey loadRsaPrivateKey(String base64Pem) {
        try {
            // Strip PEM headers if present
            String clean = base64Pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(clean);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load RSA private key — check JWT_PRIVATE_KEY", ex);
        }
    }
}
