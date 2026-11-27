package com.aiops.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 被监控的模拟业务系统：模拟订单系统，制造 CPU/内存/连接池告警，作为被运维对象。
 *
 * <p>注意：demo-app 只是「被运维的对象」，自身不持有 AIOps 的业务数据，
 * 因此排除数据源自动配置，避免因未配 datasource 而启动失败。
 * （它依赖 aiops-common 是为了复用 Result 等基础类型。）
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        SqlInitializationAutoConfiguration.class
})
@ComponentScan(basePackages = {"com.aiops.demo"})
public class DemoAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoAppApplication.class, args);
    }
}
