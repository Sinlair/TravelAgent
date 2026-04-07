package com.travalagent.amap.mcp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.travalagent.amap")
@ConfigurationPropertiesScan(basePackages = "com.travalagent.amap")
public class AmapMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmapMcpServerApplication.class, args);
    }
}
