package com.kerb.parkingadmin;

import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableVaadin("com.kerb.parkingadmin.ui")
@EnableJpaAuditing
public class ParkingAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(ParkingAdminApplication.class, args);
    }
}
