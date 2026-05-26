package com.enterprise.techdebt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TechDebtCleanupApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechDebtCleanupApplication.class, args);
    }
}
