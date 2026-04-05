package code.with.vanilson.authentication.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * UserDetailsAdapter — Domain Layer (Security Adapter)
 * <p>
 * Wraps the {@link User} domain entity to implement Spring Security's {@link UserDetails}.
 * This decouples the JPA entity from Spring Security (SRP — SOLID-S).
 * <p>
 * Use {@link #getUser()} to access the underlying domain object when you need
 * to call entity-specific methods (e.g., in AuthService).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public class UserDetailsAdapter implements UserDetails {

    private final User user;

    public UserDetailsAdapter(User user) {
        this.user = user;
    }

    /** Returns the wrapped domain {@link User} — needed by AuthService and RefreshTokenService. */
    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isAccountLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isAccountEnabled();
    }
}
