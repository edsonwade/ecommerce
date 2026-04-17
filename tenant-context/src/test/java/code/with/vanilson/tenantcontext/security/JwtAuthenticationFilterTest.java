package code.with.vanilson.tenantcontext.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtTokenValidator validator;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        validator = mock(JwtTokenValidator.class);
        filter = new JwtAuthenticationFilter(validator);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void populates_security_context_for_valid_token() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer valid.token.here");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(validator.validate("valid.token.here"))
                .thenReturn(new JwtClaims("a@b.com", 7L, "t1", "ADMIN"));

        filter.doFilter(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        SecurityPrincipal principal = (SecurityPrincipal) auth.getPrincipal();
        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.tenantId()).isEqualTo("t1");
        verify(chain).doFilter(req, res);
    }

    @Test
    void skips_context_when_no_header() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void clears_context_when_invalid_token() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer bad-token");
        when(validator.validate("bad-token"))
                .thenThrow(new InvalidJwtException("bad", null));

        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
