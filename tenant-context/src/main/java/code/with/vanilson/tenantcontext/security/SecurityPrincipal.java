package code.with.vanilson.tenantcontext.security;

public record SecurityPrincipal(String email, Long userId, String tenantId, String role) {
    public boolean isAdmin()  { return "ADMIN".equals(role); }
    public boolean isSeller() { return "SELLER".equals(role); }
    public boolean isUser()   { return "USER".equals(role); }
}
