package com.bhatn.rewardking.service;

import com.bhatn.rewardking.entity.User;
import com.bhatn.rewardking.entity.UserWallet;
import com.bhatn.rewardking.repository.UserRepository;
import com.bhatn.rewardking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final WalletRepository walletRepo;

    @Transactional
    public User syncUser(Jwt jwt) {
        String sub = jwt.getClaimAsString("sub");
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        // Safety check for the 'null name' error we saw earlier
        if (name == null && email != null) {
            name = email.split("@")[0];
        } else if (name == null) {
            name = "Valued Member";
        }

        String finalName = name;
        return userRepo.findById(sub)
                .map(existingUser -> {
                    existingUser.setEmail(email);
                    existingUser.setName(finalName);
                    return userRepo.save(existingUser);
                })
                .orElseGet(() -> {
                    // 1. Create and persist User record
                    User newUser = User.builder()
                            .cognitoId(sub)
                            .email(email)
                            .name(finalName)
                            .build();
                    User savedUser = userRepo.saveAndFlush(newUser);

                    // 2. Initialize corresponding points wallet to clear foreign key constraints
                    UserWallet wallet = UserWallet.builder()
                            .userId(sub)
                            .fullName(finalName) // Maps descriptive fields for dashboard summaries
                            .email(email)
                            .availablePoints(0L) // FIX: Switched to primitive long point assignment
                            .build();
                    walletRepo.save(wallet);

                    return savedUser;
                });
    }
}