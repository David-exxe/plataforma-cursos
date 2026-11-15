package com.academia.comprobantes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ComprobantesApplication {
    public static void main(String[] args) {
        SpringApplication.run(ComprobantesApplication.class, args);
    }
}
