package com.bhatn.cashbackking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration.class
})
// Ensure Spring looks for your repositories in the right place
@EnableJpaRepositories("com.bhatn.cashbackking.repository")
public class CashbackKingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashbackKingApplication.class, args);
    }

}