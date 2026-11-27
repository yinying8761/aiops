package com.aiops.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 登录用户上下文，由网关解析 JWT 后通过请求头透传给下游服务。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long userId;
    private String username;
    private String nickname;
    private List<String> roles;

    public static LoginUser anonymous() {
        return new LoginUser(null, "anonymous", "匿名", Collections.emptyList());
    }

    public boolean hasRole(String roleCode) {
        return roles != null && roles.contains(roleCode);
    }
}
