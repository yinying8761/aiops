package com.aiops.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Spring Cloud Gateway：路由、JWT 统一鉴权、限流。
 *
 * <p>注意：这里**不能**使用 {@code @ComponentScan("com.aiops")}，
 * 否则会扫描到 aiops-common 里的 MVC 相关 bean（MybatisPlusConfig、RedisConfig、
 * GlobalExceptionHandler 等），与 WebFlux 栈冲突导致网关启动失败。
 * 网关只需要本模块（com.aiops.gateway）与 JwtTokenProvider。
 */
@SpringBootApplication(scanBasePackages = {
        "com.aiops.gateway",
        "com.aiops.common.security"
})
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
