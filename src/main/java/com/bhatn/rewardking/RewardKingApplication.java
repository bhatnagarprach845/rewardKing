package com.bhatn.rewardking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration.class
})
// Ensure Spring looks for your repositories in the right place
@EnableJpaRepositories("com.bhatn.rewardking.repository")
public class RewardKingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RewardKingApplication.class, args);
    }

}