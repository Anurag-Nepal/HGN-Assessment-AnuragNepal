package com.hgn.sos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HgnSosApplication {

    public static void main(String[] args) {
        SpringApplication.run(HgnSosApplication.class, args);
    }
}