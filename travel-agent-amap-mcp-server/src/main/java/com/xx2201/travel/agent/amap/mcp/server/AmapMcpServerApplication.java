package com.xx2201.travel.agent.amap.mcp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.xx2201.travel.agent.amap")
@ConfigurationPropertiesScan(basePackages = "com.xx2201.travel.agent.amap")
public class AmapMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmapMcpServerApplication.class, args);
    }
}
