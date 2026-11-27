package com.aiops.gateway.config;

import com.aiops.common.security.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关的公共组件注册。
 *
 * <p>网关模块无法像业务服务那样依赖 web 相关的自动配置，
 * 因此把 JwtTokenProvider 显式声明为 bean。
 */
@Configuration
public class GatewayBeanConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider();
    }
}
