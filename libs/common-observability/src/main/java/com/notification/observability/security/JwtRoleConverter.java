package com.notification.observability.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Maps a JWT's single "role" claim (SERVICE / ADMIN / USER - PRD section 44) into a
 * Spring Security authority. Deliberately simple: one role per token, no scopes, no
 * per-resource permission lists - this project has three roles total and doesn't
 * need more than that.
 * <p>
 * Shared here because every service that validates JWTs needs to do this identical
 * mapping; the actual authorization RULES (which endpoint needs which role) still
 * live in each service's own SecurityConfig, since those genuinely differ per service.
 */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
