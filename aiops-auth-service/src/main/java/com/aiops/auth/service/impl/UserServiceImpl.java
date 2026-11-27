package com.aiops.auth.service.impl;

import com.aiops.auth.dto.LoginRequest;
import com.aiops.auth.dto.LoginResponse;
import com.aiops.auth.dto.UserSaveRequest;
import com.aiops.auth.dto.UserView;
import com.aiops.auth.entity.SysRole;
import com.aiops.auth.entity.SysUser;
import com.aiops.auth.mapper.SysRoleMapper;
import com.aiops.auth.mapper.SysUserMapper;
import com.aiops.auth.service.UserService;
import com.aiops.common.api.PageResult;
import com.aiops.common.api.ResultCode;
import com.aiops.common.constant.CommonConstants;
import com.aiops.common.exception.BizException;
import com.aiops.common.security.JwtTokenProvider;
import com.aiops.common.security.LoginUser;
import com.aiops.common.security.UserContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 用户与权限服务实现。
 * <p>关键点：
 * <ul>
 *   <li>密码用 BCrypt 校验，数据库不存明文；</li>
 *   <li>登录失败统一返回「用户名或密码错误」，不区分用户不存在/密码错，避免账号枚举；</li>
 *   <li>角色编码随 JWT 下发，网关与业务服务据此做接口级鉴权。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public LoginResponse login(LoginRequest request) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername().trim())
                .last("LIMIT 1");
        SysUser user = userMapper.selectOne(wrapper);

        // 统一失败提示，避免通过响应差异枚举账号
        if (user == null || !PASSWORD_ENCODER.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败 username={}", request.getUsername());
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (!Objects.equals(user.getStatus(), 1)) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        if (roles == null || roles.isEmpty()) {
            roles = Collections.emptyList();
        }

        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getUsername(), user.getNickname(), roles);

        log.info("登录成功 username={} userId={} roles={}", user.getUsername(), user.getId(), roles);

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpireSeconds())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(roles)
                .build();
    }

    @Override
    public UserView findByUsername(String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toView(user, userMapper.selectRoleCodesByUserId(user.getId()));
    }

    @Override
    public UserView currentUser() {
        LoginUser loginUser = UserContextHolder.current();
        if (loginUser.getUserId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户已不存在");
        }
        return toView(user, userMapper.selectRoleCodesByUserId(user.getId()));
    }

    @Override
    public PageResult<UserView> page(long pageNum, long pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(status != null, SysUser::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getNickname, keyword))
                .orderByDesc(SysUser::getId);

        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<UserView> records = page.getRecords().stream()
                .map(u -> toView(u, userMapper.selectRoleCodesByUserId(u.getId())))
                .toList();
        return new PageResult<>(records, page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(UserSaveRequest request) {
        boolean isCreate = request.getId() == null;

        // 用户名唯一性校验（排除自身）
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername().trim())
                .ne(!isCreate, SysUser::getId, request.getId()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setId(request.getId());
        user.setUsername(request.getUsername().trim());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRemark(request.getRemark());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());

        if (isCreate) {
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "新增用户必须设置密码");
            }
            user.setPassword(PASSWORD_ENCODER.encode(request.getPassword()));
            userMapper.insert(user);
        } else {
            // 密码留空表示不修改
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                user.setPassword(PASSWORD_ENCODER.encode(request.getPassword()));
            }
            userMapper.updateById(user);
        }

        if (request.getRoleCodes() != null) {
            bindRoles(user.getId(), request.getRoleCodes());
        }
        return user.getId();
    }

    private void bindRoles(Long userId, List<String> roleCodes) {
        userMapper.deleteUserRoles(userId);
        if (roleCodes.isEmpty()) {
            return;
        }
        List<Long> roleIds = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getRoleCode, roleCodes))
                .stream().map(SysRole::getId).toList();
        if (roleIds.isEmpty()) {
            return;
        }
        userMapper.insertUserRoles(userId, roleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (Objects.equals(id, 1L)) {
            throw new BizException(ResultCode.PARAM_ERROR, "内置管理员不可删除");
        }
        userMapper.deleteById(id);
        userMapper.deleteUserRoles(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (Objects.equals(id, 1L) && !Objects.equals(status, 1)) {
            throw new BizException(ResultCode.PARAM_ERROR, "内置管理员不可禁用");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public List<String> listRoleCodes() {
        return roleMapper.selectList(null).stream().map(SysRole::getRoleCode).toList();
    }

    private UserView toView(SysUser user, List<String> roles) {
        UserView view = new UserView();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setEmail(user.getEmail());
        view.setPhone(user.getPhone());
        view.setStatus(user.getStatus());
        view.setRemark(user.getRemark());
        view.setCreateTime(user.getCreateTime());
        view.setRoles(roles == null ? Collections.emptyList() : roles);
        return view;
    }

    /** 供后续服务复用：生成 BCrypt 密文 */
    public static String encode(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }
}
