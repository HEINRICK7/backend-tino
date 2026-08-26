package com.tino.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class TinoBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(TinoBackendApplication.class, args);
    }
}
