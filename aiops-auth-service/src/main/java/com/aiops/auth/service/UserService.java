package com.aiops.auth.service;

import com.aiops.auth.dto.LoginRequest;
import com.aiops.auth.dto.LoginResponse;
import com.aiops.auth.dto.UserSaveRequest;
import com.aiops.auth.dto.UserView;
import com.aiops.common.api.PageResult;

import java.util.List;

/**
 * 用户与权限服务。
 */
public interface UserService {

    /**
     * 登录，成功返回 JWT 与用户画像。
     */
    LoginResponse login(LoginRequest request);

    /**
     * 按用户名查询用户画像（含角色）。
     */
    UserView findByUsername(String username);

    /**
     * 当前登录用户信息。
     */
    UserView currentUser();

    /**
     * 分页查询用户。
     */
    PageResult<UserView> page(long pageNum, long pageSize, String keyword, Integer status);

    /**
     * 新增或更新用户。id 为空表示新增。
     */
    Long save(UserSaveRequest request);

    /**
     * 删除用户（逻辑删除）。
     */
    void delete(Long id);

    /**
     * 启用/禁用用户。
     */
    void updateStatus(Long id, Integer status);

    /**
     * 查询全部角色（供分配下拉框）。
     */
    List<String> listRoleCodes();
}
