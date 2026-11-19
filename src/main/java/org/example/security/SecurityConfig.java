package org.example.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST API olduğu için CSRF'i kapatıyoruz
                .csrf(AbstractHttpConfigurer::disable)
                // CORS yapılandırması
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // JWT kullandığımız için session tutmuyoruz (Stateless)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Yetkilendirme kuralları
                .authorizeHttpRequests(auth -> auth
                        // OAuth giriş ve callback endpoint'leri herkese açık
                        .requestMatchers("/login", "/api/auth/login", "/api/auth/callback",
                                "/api/v1/auth/callback", "/api/v1/auth/exchange").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/").permitAll()
                        // ClickUp webhook is authenticated by its signature in WebhookIngestionService
                        .requestMatchers("/api/webhooks/**").permitAll()
                        // Swagger & OpenAPI dokümantasyonu herkese açık
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Actuator sağlık kontrolü herkese açık
                        .requestMatchers("/actuator/**").permitAll()
                        // Diğer tüm endpoint'ler JWT ile yetkilendirme gerektirir
                        .anyRequest().authenticated()
                )
                // Yetkisiz erişimlerde 401 Unauthorized dön
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Yetkisiz erişim: Lütfen geçerli bir JWT token sağlayın."))
                )
                // Bizim JWT filtremizi Spring Security filtre zincirine ekliyoruz
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); // Geliştirme aşamasında tüm origin'lere izin verir
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
