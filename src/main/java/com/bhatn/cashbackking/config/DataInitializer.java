package com.bhatn.cashbackking.config;

import com.bhatn.cashbackking.entity.User;
import com.bhatn.cashbackking.entity.UserWallet;
import com.bhatn.cashbackking.repository.UserRepository;
import com.bhatn.cashbackking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Configuration
@RequiredArgsConstructor
@Profile("default") // This runs when you start the app locally
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final WalletRepository walletRepo;

    @Override
    @Transactional
    public void run(String... args) {
        String localId = "local-user";
        
        if (!userRepo.existsById(localId)) {
            User user = User.builder()
                    .cognitoId(localId)
                    .email("prachi@example.com")
                    .name("Prachi Bhatnagar")
                    .build();
            userRepo.save(user);

            UserWallet wallet = UserWallet.builder()
                    .userId(localId)
                    .currentBalance(new BigDecimal("100.00")) // Start with some test money
                    .build();
            walletRepo.save(wallet);
            
            System.out.println("Prachi :: Local test user and wallet initialized.");
        }
    }
}