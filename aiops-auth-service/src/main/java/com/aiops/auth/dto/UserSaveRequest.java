package com.aiops.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/修改请求（管理员使用）。
 */
@Data
public class UserSaveRequest {

    /** 修改时必填；新增时留空 */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度须为 3~50 位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    /** 新增时必填；修改时留空表示不改密码 */
    @Size(min = 6, max = 64, message = "密码长度须为 6~64 位")
    private String password;

    @Size(max = 50, message = "昵称长度不能超过 50 位")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private Integer status;

    private String remark;

    /** 分配的角色编码列表 */
    private List<String> roleCodes;
}
