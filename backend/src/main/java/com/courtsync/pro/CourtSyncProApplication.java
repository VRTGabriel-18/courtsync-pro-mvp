package com.courtsync.pro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CourtSyncProApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourtSyncProApplication.class, args);
    }
}
