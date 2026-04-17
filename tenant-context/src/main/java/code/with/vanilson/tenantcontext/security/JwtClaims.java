package code.with.vanilson.tenantcontext.security;

public record JwtClaims(String subject, Long userId, String tenantId, String role) {}
