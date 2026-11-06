package com.example.logistics.identity.config;

import com.example.logistics.identity.application.IdentityPermissionEvaluator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentitySecurityConfiguration {
    @Bean SecretKey jwtSecretKey(IdentityProperties properties) {
        byte[] key = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 bytes");
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean JwtDecoder jwtDecoder(SecretKey key, IdentityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(jwt -> {
            var issueResult = issuer.validate(jwt);
            return issueResult.hasErrors() ? issueResult : audience.validate(jwt);
        });
        return decoder;
    }

    @Bean org.springframework.core.convert.converter.Converter<Jwt, org.springframework.security.authentication.AbstractAuthenticationToken> jwtAuthenticationConverter() {
        var authorities = new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("authorities");
        authorities.setAuthorityPrefix("");
        var delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(authorities);
        return jwt -> {
            if (!"access".equals(jwt.getClaimAsString("purpose"))) {
                throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(new OAuth2Error(
                        "invalid_token", "MFA challenge tokens cannot authorize API requests", null));
            }
            return delegate.convert(jwt);
        };
    }

    @Bean CookieCsrfTokenRepository csrfTokenRepository(IdentityProperties properties) {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").secure(properties.secureCookies())
                .sameSite("Strict"));
        return repository;
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean org.springframework.security.access.expression.method.MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            IdentityPermissionEvaluator permissionEvaluator) {
        var handler = new org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrf,
                                                  org.springframework.core.convert.converter.Converter<Jwt, org.springframework.security.authentication.AbstractAuthenticationToken> converter,
                                                  com.example.logistics.identity.infrastructure.DeviceSessionRepository sessions, java.time.Clock clock) throws Exception {
        http.csrf(config -> config.csrfTokenRepository(csrf)
                        .ignoringRequestMatchers("/api/v1/tracking/positions"))
                .cors(Customizer.withDefaults())
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers("/api/v1/tracking/public/**").permitAll()
                        .requestMatchers("/api/v1/tracking/positions").permitAll()
                        .requestMatchers("/ws/tracking/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        http.addFilterAfter(new DeviceSessionValidationFilter(sessions, clock), org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean CorsConfigurationSource corsConfigurationSource(IdentityProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(properties.appBaseUrl()));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Correlation-ID", "If-Match", "X-Device-Id", "X-Device-Token"));
        cors.setExposedHeaders(List.of("X-Correlation-ID", "Retry-After"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
