package com.bhatn.rewardking.config;

import com.bhatn.rewardking.entity.User;
import com.bhatn.rewardking.entity.UserWallet;
import com.bhatn.rewardking.repository.UserRepository;
import com.bhatn.rewardking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j // FIX 2: Added annotation to dynamically inject the 'log' handle bean instance
@Profile("default")
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
                    .fullName("Prachi Bhatnagar") // Enriched for dashboard responses
                    .email("prachi@example.com")
                    .availablePoints(100L) // Casted cleanly to primitive long tracking
                    .build();
            walletRepo.save(wallet);

            log.info("Prachi :: Local test user and wallet initialized safely.");
        }
        else {
            log.info("Prachi :: Local test profile already present in database. Skipping data seed.");
        }
    }
}