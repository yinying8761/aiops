package com.aiops.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表/详情响应。
 */
@Data
public class UserView {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private List<String> roles;
}
