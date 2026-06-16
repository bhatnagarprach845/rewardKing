package com.bhatn.rewardking.config;

import com.bhatn.rewardking.entity.UserWallet;
import com.bhatn.rewardking.repository.WalletRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class SeedUserConfig {
    @Bean
    @Transactional
    CommandLineRunner initDatabase(WalletRepository repository) {
        return args -> {
            if (repository.findByUserId("local-user").isEmpty()) {
                repository.save(UserWallet.builder()
                        .userId("local-user")
                                .availablePoints(100)
                        .build());
                System.out.println("Seed user 'local-user' created.");
            }
        };
    }
}
