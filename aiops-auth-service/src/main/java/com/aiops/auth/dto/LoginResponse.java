package com.aiops.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应：Token 与前端渲染所需的用户画像。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private String tokenType;

    /** 有效期（秒） */
    private Long expiresIn;

    private Long userId;

    private String username;

    private String nickname;

    /** 角色编码列表，前端据此控制菜单与按钮 */
    private List<String> roles;
}
