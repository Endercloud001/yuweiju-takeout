package com.codeying;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 *
 * @author Endercloudsele
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.codeying")
@MapperScan("com.codeying.mapper")
@EnableScheduling
public class App {
    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        SpringApplication.run(App.class,args);
    }
}
