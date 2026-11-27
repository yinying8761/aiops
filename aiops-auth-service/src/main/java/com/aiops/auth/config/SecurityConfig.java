package com.aiops.auth.config;

import com.aiops.common.security.JwtTokenProvider;
import com.aiops.common.security.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 安全配置。
 * <p>本项目由 Gateway 统一做 JWT 鉴权，业务服务内部只需把网关注入的
 * X-User-* 头还原成 SecurityContext，避免重复解析 Token 造成密钥扩散。
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/actuator/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().permitAll())
                .addFilterBefore(new HeaderAuthFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 从网关透传的请求头还原登录态；若请求直接打到本服务并携带了 Token，则自行解析兜底。
     */
    static class HeaderAuthFilter extends OncePerRequestFilter {

        private final JwtTokenProvider jwtTokenProvider;

        HeaderAuthFilter(JwtTokenProvider jwtTokenProvider) {
            this.jwtTokenProvider = jwtTokenProvider;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String uid = request.getHeader("X-User-Id");
            String username = request.getHeader("X-Username");
            String rolesHeader = request.getHeader("X-Roles");

            LoginUser loginUser = null;
            if (uid != null && !uid.isBlank()) {
                List<String> roles = rolesHeader == null || rolesHeader.isBlank()
                        ? List.of() : List.of(rolesHeader.split(","));
                loginUser = new LoginUser(Long.valueOf(uid), username, username, roles);
            } else {
                // 兜底：直连本服务且自带 Authorization 的场景（便于单服务调试）
                String token = jwtTokenProvider.resolveToken(request.getHeader("Authorization"));
                if (token != null) {
                    var claims = jwtTokenProvider.parseQuietly(token);
                    if (claims != null) {
                        Object uidClaim = claims.get("uid");
                        @SuppressWarnings("unchecked")
                        List<String> roles = (List<String>) claims.get("roles");
                        loginUser = new LoginUser(
                                uidClaim == null ? null : Long.valueOf(uidClaim.toString()),
                                (String) claims.get("username"),
                                (String) claims.get("nickname"),
                                roles == null ? List.of() : roles);
                    }
                }
            }

            if (loginUser != null && loginUser.getUserId() != null) {
                var authorities = loginUser.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(
                        loginUser, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            chain.doFilter(request, response);
        }
    }
}
