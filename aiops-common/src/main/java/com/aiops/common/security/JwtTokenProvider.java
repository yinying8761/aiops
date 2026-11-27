package com.aiops.common.security;

import com.aiops.common.constant.CommonConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 签发与解析工具。auth-service 使用签发方法，gateway 与各业务服务使用解析方法。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** 签名密钥，生产环境务必通过配置中心/环境变量注入 */
    @Value("${aiops.jwt.secret:aiops-platform-sj004106-not-want-to-move-group-2026-secret-key}")
    private String secret;

    /** Token 有效期（秒），默认 8 小时，覆盖一个值班班次 */
    @Getter
    @Value("${aiops.jwt.expire-seconds:28800}")
    private long expireSeconds;

    @Value("${aiops.jwt.issuer:aiops-platform}")
    private String issuer;

    private SecretKey secretKey;

    private SecretKey key() {
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        return secretKey;
    }

    /**
     * 签发 Token。
     *
     * @param userId   用户ID
     * @param username 登录名
     * @param nickname 昵称
     * @param roles    角色编码列表，如 [ADMIN, OPS]
     */
    public String generateToken(Long userId, String username, String nickname, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireSeconds * 1000);

        Map<String, Object> claims = new HashMap<>(4);
        claims.put(CommonConstants.JWT_CLAIM_USER_ID, userId);
        claims.put(CommonConstants.JWT_CLAIM_USERNAME, username);
        claims.put(CommonConstants.JWT_CLAIM_NICKNAME, nickname);
        claims.put(CommonConstants.JWT_CLAIM_ROLES, roles);

        return Jwts.builder()
                .claims(claims)
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /**
     * 解析并校验 Token。签名错误、过期、格式错误都会抛出 JwtException。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 安全解析：失败返回 null，用于网关过滤器的「尽力而为」场景。
     */
    public Claims parseQuietly(String token) {
        try {
            return parse(token);
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Authorization 头中剥离 "Bearer " 前缀。
     */
    public String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (authorizationHeader.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return authorizationHeader.substring(CommonConstants.TOKEN_PREFIX.length()).trim();
        }
        return authorizationHeader.trim();
    }
}
