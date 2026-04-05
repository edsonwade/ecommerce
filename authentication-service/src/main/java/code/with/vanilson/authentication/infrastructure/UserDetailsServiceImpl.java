package code.with.vanilson.authentication.infrastructure;

import code.with.vanilson.authentication.domain.UserDetailsAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * UserDetailsServiceImpl — Infrastructure Layer (Security)
 * <p>
 * Loads users from the database by email for Spring Security authentication.
 * Implements UserDetailsService — the hook Spring Security calls during login.
 * <p>
 * Single Responsibility (SOLID-S): only user-loading for Spring Security.
 * Dependency Inversion (SOLID-D): depends on UserRepository abstraction.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository  userRepository;
    private final MessageSource   messageSource;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(UserDetailsAdapter::new)
                .orElseThrow(() -> {
                    String message = messageSource.getMessage(
                            "auth.user.not.found", new Object[]{email},
                            LocaleContextHolder.getLocale());
                    log.warn("[UserDetailsService] User not found: email=[{}]", email);
                    // Must throw UsernameNotFoundException (not a custom subclass) so that
                    // DaoAuthenticationProvider.hideUserNotFoundExceptions converts it to
                    // BadCredentialsException — preventing user enumeration and 500 errors.
                    return new UsernameNotFoundException(message);
                });
    }
}
