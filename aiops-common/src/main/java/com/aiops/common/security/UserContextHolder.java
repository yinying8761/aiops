package com.aiops.common.security;

import com.aiops.common.constant.CommonConstants;
import com.aiops.common.exception.BizException;
import com.aiops.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 从当前请求头读取网关透传的登录用户信息。
 * 业务服务不重复解析 JWT，只信任网关写入的 X-User-* 头，保持职责单一。
 */
public final class UserContextHolder {

    private UserContextHolder() {
    }

    public static LoginUser current() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return LoginUser.anonymous();
        }
        HttpServletRequest request = attrs.getRequest();
        String uid = request.getHeader(CommonConstants.HEADER_USER_ID);
        if (uid == null || uid.isBlank()) {
            return LoginUser.anonymous();
        }
        List<String> roles = Collections.emptyList();
        String rolesHeader = request.getHeader(CommonConstants.HEADER_ROLES);
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            roles = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        String username = decode(request.getHeader(CommonConstants.HEADER_USERNAME));
        return new LoginUser(Long.valueOf(uid), username, username, roles);
    }

    /**
     * 取当前用户ID，未登录时抛出 401。
     */
    public static Long requireUserId() {
        LoginUser user = current();
        if (user.getUserId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user.getUserId();
    }

    /**
     * 取当前用户名，未登录返回 system（用于系统自动触发的操作留痕）。
     */
    public static String currentUsernameOrSystem() {
        LoginUser user = current();
        return user.getUsername() == null ? CommonConstants.SYSTEM_OPERATOR : user.getUsername();
    }

    private static String decode(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }
}
