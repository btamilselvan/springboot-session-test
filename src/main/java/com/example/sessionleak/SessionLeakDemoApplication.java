package com.example.sessionleak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SessionLeakDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SessionLeakDemoApplication.class, args);
    }
}
