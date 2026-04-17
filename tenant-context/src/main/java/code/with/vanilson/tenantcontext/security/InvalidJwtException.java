package code.with.vanilson.tenantcontext.security;

public class InvalidJwtException extends RuntimeException {
    public InvalidJwtException(String msg, Throwable cause) { super(msg, cause); }
}
