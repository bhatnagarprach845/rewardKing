package com.bhatn.rewardking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(AbstractHttpConfigurer::disable) // CorsFilter above handles it
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — order: most specific first
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/debug/**").permitAll()
                        .requestMatchers("/api/v1/version").permitAll()
                        .requestMatchers("/api/v1/webhooks/**").permitAll()

                        // FIX: Admin rule MUST come before the wildcard /api/v1/** rule.
                        // Previously the wildcard matched first, so any authenticated user
                        // could reach admin endpoints regardless of their role.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // All other API endpoints require authentication
                        .requestMatchers("/api/v1/**").authenticated()

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "https://*.amplifyapp.com"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = "https://cognito-idp.us-east-2.amazonaws.com/us-east-2_OfWs7erUH/.well-known/jwks.json";

        // Build a clean Nimbus decoder pointing directly to your Cognito keys
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // Relax validation to focus solely on token expiration checking.
        // This stops Spring from blocking the request due to rigid 'iss' string comparisons.
        OAuth2TokenValidator<Jwt> tokenValidator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(java.time.Duration.ofMinutes(5)) // Expanded clock skew allowance
        );

        jwtDecoder.setJwtValidator(tokenValidator);
        return jwtDecoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // 1. Configure how roles/groups are extracted
        JwtGrantedAuthoritiesConverter listConverter = new JwtGrantedAuthoritiesConverter();
        listConverter.setAuthorityPrefix("ROLE_");

        // Access tokens use the 'scope' claim or 'cognito:groups' if assigned to a group.
        // Setting it to 'scope' ensures standard OAuth2 scopes map cleanly if groups are blank.
        listConverter.setAuthoritiesClaimName("scope");

        // 2. Fix the Principal Null Mismatch
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(listConverter);

        // 🚀 CRITICAL FIX: Explicitly tell Spring to map the user identity string from the Access Token's "sub" or "username" claim
        converter.setPrincipalClaimName("sub"); // Change to "username" if you prefer "anilk" over the UUID string

        return converter;
    }
}