package code.with.vanilson.tenantcontext.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

@Component
public class JwtTokenValidator {

    private final SecretKey signingKey;

    public JwtTokenValidator(@Value("${application.security.jwt.secret-key}") String secretKeyBase64) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKeyBase64));
    }

    public JwtClaims validate(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new JwtClaims(
                    c.getSubject(),
                    c.get("userId", Long.class),
                    c.get("tenantId", String.class),
                    c.get("role", String.class)
            );
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidJwtException("JWT validation failed: " + ex.getMessage(), ex);
        }
    }
}
