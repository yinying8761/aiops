package com.aiops.gateway.filter;

import com.aiops.common.api.Result;
import com.aiops.common.api.ResultCode;
import com.aiops.common.constant.CommonConstants;
import com.aiops.common.security.JwtTokenProvider;
import com.aiops.common.util.JsonUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 统一鉴权过滤器。
 *
 * <p>对应文档 2.1「网关层：Spring Cloud Gateway（路由、JWT 统一鉴权、限流）」与 TC-02「无权限访问返回 401」。
 *
 * <p>职责：
 * <ol>
 *   <li>放行白名单（登录、健康检查、内部接口）；</li>
 *   <li>校验 Token 有效性，失效则返回 401；</li>
 *   <li>校验通过后把用户信息写入请求头，下游服务直接读取，无需重复解析 Token。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtTokenProvider jwtTokenProvider;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 无需认证的路径 */
    private static final List<String> WHITE_LIST = List.of(
            "/auth/login",
            "/auth/logout",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/doc.html",
            "/favicon.ico",
            // 内部服务间调用：由内网保护，不经过用户认证
            "/alerts",
            "/alerts/**",
            "/agent/analyze/async/**",
            "/tickets/auto",
            "/knowledge/search",
            "/metrics/**",
            "/demo/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 预检请求直接放行
        if (org.springframework.http.HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // 白名单放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        String token = jwtTokenProvider.resolveToken(
                request.getHeaders().getFirst(CommonConstants.HEADER_AUTHORIZATION));
        if (token == null) {
            return unauthorized(exchange, ResultCode.UNAUTHORIZED, "未携带访问令牌");
        }

        Claims claims = jwtTokenProvider.parseQuietly(token);
        if (claims == null) {
            return unauthorized(exchange, ResultCode.TOKEN_INVALID, "令牌无效或已过期");
        }

        // 把用户信息透传给下游，避免每个服务重复解析
        Object uid = claims.get(CommonConstants.JWT_CLAIM_USER_ID);
        Object username = claims.get(CommonConstants.JWT_CLAIM_USERNAME);
        Object nickname = claims.get(CommonConstants.JWT_CLAIM_NICKNAME);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get(CommonConstants.JWT_CLAIM_ROLES);

        ServerHttpRequest mutated = request.mutate()
                .header(CommonConstants.HEADER_USER_ID, uid == null ? "" : uid.toString())
                .header(CommonConstants.HEADER_USERNAME, encode(nickname == null
                        ? String.valueOf(username) : nickname.toString()))
                .header(CommonConstants.HEADER_ROLES, roles == null ? "" : String.join(",", roles))
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhiteListed(String path) {
        for (String pattern : WHITE_LIST) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 中文昵称需要 URL 编码，避免 HTTP 头出现非 ASCII 字符导致下游解析异常。
     */
    private String encode(String raw) {
        return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }

    /**
     * 统一返回 401 JSON，前端拦截器据此跳转登录页。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Void> body = Result.fail(code, message);
        DataBuffer buffer = response.bufferFactory()
                .wrap(JsonUtils.toJson(body).getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 早于路由过滤器执行
        return -100;
    }
}
