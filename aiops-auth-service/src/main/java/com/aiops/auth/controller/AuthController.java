package com.aiops.auth.controller;

import com.aiops.auth.dto.LoginRequest;
import com.aiops.auth.dto.LoginResponse;
import com.aiops.auth.dto.UserSaveRequest;
import com.aiops.auth.dto.UserView;
import com.aiops.auth.service.UserService;
import com.aiops.common.api.PageResult;
import com.aiops.common.api.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户与权限接口。
 * <p>对应文档 2.6 接口设计：POST /auth/login。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 用户登录，返回 JWT。对应 TC-01。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", userService.login(request));
    }

    /**
     * 当前登录用户信息（前端刷新页面后恢复用户态）。
     */
    @GetMapping("/me")
    public Result<UserView> me() {
        return Result.ok(userService.currentUser());
    }

    /**
     * 注销。JWT 无状态，服务端仅记录日志；如需强制失效可在网关侧加黑名单。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok("已退出登录", null);
    }

    /**
     * 用户分页查询。
     */
    @GetMapping("/users")
    public Result<PageResult<UserView>> pageUsers(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.ok(userService.page(pageNum, pageSize, keyword, status));
    }

    /**
     * 新增或更新用户。
     */
    @PostMapping("/users")
    public Result<Long> saveUser(@Valid @RequestBody UserSaveRequest request) {
        return Result.ok(request.getId() == null ? "新增成功" : "更新成功", userService.save(request));
    }

    /**
     * 更新用户（RESTful 风格，等价于 POST /auth/users + id）。
     */
    @PutMapping("/users/{id}")
    public Result<Long> updateUser(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        request.setId(id);
        return Result.ok("更新成功", userService.save(request));
    }

    /**
     * 删除用户。
     */
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok("删除成功", null);
    }

    /**
     * 启用/禁用用户。
     */
    @PutMapping("/users/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.ok("状态已更新", null);
    }

    /**
     * 角色编码列表（供分配下拉框）。
     */
    @GetMapping("/roles")
    public Result<List<String>> listRoles() {
        return Result.ok(userService.listRoleCodes());
    }
}
