package code.with.vanilson.tenantcontext.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

// Use string-based class names so Spring Boot evaluates conditions via ASM bytecode
// inspection rather than class loading. This prevents NoClassDefFoundError in services
// that have spring-security-web on the test classpath but lack spring-security-config.
@Configuration
@ConditionalOnClass(name = {
        "org.springframework.security.web.SecurityFilterChain",
        "org.springframework.security.config.annotation.web.builders.HttpSecurity"
})
@ConditionalOnMissingBean(type = "org.springframework.security.web.SecurityFilterChain")
@ComponentScan(basePackageClasses = JwtTokenValidator.class)
public class JwtSecurityAutoConfiguration {}
