package com.bhatn.cashbackking.config;

import com.bhatn.cashbackking.entity.UserWallet;
import com.bhatn.cashbackking.repository.WalletRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Configuration
public class SeedUserConfig {
    @Bean
    @Transactional
    CommandLineRunner initDatabase(WalletRepository repository) {
        return args -> {
            if (repository.findByUserId("local-user").isEmpty()) {
                repository.save(UserWallet.builder()
                        .userId("local-user")
                        .currentBalance(BigDecimal.ZERO)
                        .build());
                System.out.println("Seed user 'local-user' created.");
            }
        };
    }
}
