package com.notification.event.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.observability.error.ErrorResponse;
import com.notification.observability.security.JwtDecoders;
import com.notification.observability.security.JwtRoleConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Only POST /api/v1/events is guarded (PRD section 44: SERVICE role publishes
 * events; ADMIN can too, since admin is a superset in this design). Actuator stays
 * open - a health check gated behind auth would break Prometheus/Docker healthchecks,
 * and this is local infra, not a public deployment.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
        return JwtDecoders.hmac256(secret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());

        AuthenticationEntryPoint unauthorized = (request, response, ex) ->
                writeError(response, objectMapper, 401, "UNAUTHORIZED", "Missing or invalid JWT", request.getRequestURI());
        AccessDeniedHandler forbidden = (request, response, ex) ->
                writeError(response, objectMapper, 403, "FORBIDDEN", "Insufficient role for this operation", request.getRequestURI());

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/events").hasAnyRole("SERVICE", "ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(unauthorized)
                .accessDeniedHandler(forbidden))
            .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, ObjectMapper objectMapper,
                             int status, String error, String message, String path) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(status, error, message, path, null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
