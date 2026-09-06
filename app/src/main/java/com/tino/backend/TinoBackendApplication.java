package com.tino.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
@Import(com.tino.backend.foundation.SecurityFoundationConfiguration.class)
public class TinoBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(TinoBackendApplication.class, args);
    }
}
