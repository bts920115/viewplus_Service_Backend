package com.bts.viewplustest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.bts.viewplustest.auth.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 테스트 단계에서는 뷰어와 WebSocket 시그널링을 익명으로 공개한다.
     * 회원 기능을 추가할 때는 이 지점에서 인증 규칙을 역할별로 분리한다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http)
            throws Exception {

        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/**", "/web/auth/**", "/actuator/health")
                        .permitAll()
                        .requestMatchers("/ws/**")
                        .permitAll()

                        // Tailscale 네트워크의 브라우저 수신 페이지와 정적 자원은
                        // 로그인 없이 열어야 WebSocket 협상을 시작할 수 있다.
                        .requestMatchers("/", "/viewer", "/viewer/**")
                        .permitAll()

                        .anyRequest()
                        .authenticated()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers(request ->
                        request.getServletPath().startsWith("/api/auth/") ||
                        (request.getHeader("Authorization") != null &&
                         request.getHeader("Authorization").startsWith("Bearer "))))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"로그인이 만료되었거나 인증 정보가 없습니다. 다시 로그인하세요.\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"이 작업에 대한 접근 권한이 없습니다.\"}");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
