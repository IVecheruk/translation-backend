package com.translatelab.backend.config;

import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.auth.security.RefreshRequestGuardFilter;
import com.translatelab.backend.common.web.ApiRateLimitFilter;
import com.translatelab.backend.common.web.RateLimitResponseWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties({
        ApiRateLimitProperties.class,
        AccountSecurityProperties.class,
        WebSecurityProperties.class,
        RefreshTokenProperties.class
})
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestSecurityErrorHandler securityErrorHandler,
            ApiRateLimitFilter apiRateLimitFilter,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            WebSecurityProperties properties
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource(properties)
                ))
                .authorizeHttpRequests(authorize -> {
                        authorize.requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/email-verification/request",
                                "/api/auth/email-verification/confirm",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/subscription-offers"
                        ).permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/livez",
                                "/readyz"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payments/webhooks/tribute"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/documents/upload",
                                "/api/subscription-purchases"
                        ).access((authentication, context) -> {
                            Object principal = authentication.get().getPrincipal();
                            if (!(principal instanceof Jwt jwt)) {
                                return new AuthorizationDecision(false);
                            }
                            Boolean verified = jwt.getClaimAsBoolean(
                                    "email_verified"
                            );
                            return new AuthorizationDecision(
                                    !Boolean.FALSE.equals(verified)
                            );
                        });
                        if (properties.openApiPublicAccess()) {
                            authorize.requestMatchers(
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html"
                            ).permitAll();
                        } else {
                            authorize.requestMatchers(
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html"
                            ).authenticated();
                        }
                        authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(request -> {
                            // Cookie actions must work even if an interceptor sends
                            // an expired access token along with the request.
                            if (RefreshRequestGuardFilter.isCookieAction(request)) {
                                return null;
                            }
                            return new DefaultBearerTokenResolver().resolve(request);
                        })
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter
                        ))
                )
                .addFilterBefore(new RefreshRequestGuardFilter(securityErrorHandler),
                        BearerTokenAuthenticationFilter.class)
                .addFilterAfter(
                        apiRateLimitFilter,
                        BearerTokenAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken>
            jwtAuthenticationConverter() {
        return jwt -> {
            List<SimpleGrantedAuthority> authorities = Boolean.TRUE.equals(
                    jwt.getClaimAsBoolean("email_verified")
            )
                    ? List.of(new SimpleGrantedAuthority("EMAIL_VERIFIED"))
                    : List.of();
            return new JwtAuthenticationToken(
                    jwt,
                    authorities,
                    jwt.getSubject()
            );
        };
    }

    @Bean
    public ApiRateLimitFilter apiRateLimitFilter(
            ApiRateLimitProperties properties,
            RateLimitResponseWriter responseWriter
    ) {
        return new ApiRateLimitFilter(
                properties,
                responseWriter,
                Clock.systemUTC()
        );
    }

    @Bean
    public RateLimitResponseWriter rateLimitResponseWriter(
            ObjectMapper objectMapper
    ) {
        return new RateLimitResponseWriter(objectMapper);
    }

    @Bean
    public FilterRegistrationBean<ApiRateLimitFilter>
            disableContainerRateLimitRegistration(
                    ApiRateLimitFilter filter
            ) {
        FilterRegistrationBean<ApiRateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            WebSecurityProperties properties
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        if (properties.corsEnabled()) {
            configuration.setAllowedOrigins(properties.allowedOrigins());
            configuration.setAllowedMethods(
                    List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
            );
            configuration.setAllowedHeaders(
                    List.of("Authorization", "Content-Type", "X-Correlation-ID", "X-Refresh-Request")
            );
            configuration.setExposedHeaders(
                    List.of("X-Correlation-ID", "Retry-After")
            );
            configuration.setAllowCredentials(true);
            configuration.setMaxAge(3600L);
        }
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
