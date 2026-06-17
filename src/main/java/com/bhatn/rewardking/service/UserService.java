package com.bhatn.rewardking.service;

import com.bhatn.rewardking.controller.UserController.UserSyncRequest; // Added import
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
    public User syncUser(Jwt jwt, UserSyncRequest request) { // 🚀 FIX: Accepting DTO reference
        // 1. Authenticate identity from secure token claim
        String sub = jwt.getClaimAsString("sub");

        // 2. Extract profile fields from request body payload
        String email = request.email();
        String name = request.name();

        // 3. Keep your robust safety fallbacks fully active
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
                    // Create and persist User record safely with non-null fields
                    User newUser = User.builder()
                            .cognitoId(sub)
                            .email(email)
                            .name(finalName)
                            .build();
                    User savedUser = userRepo.saveAndFlush(newUser);

                    // Initialize point tracking wallet matching the record ID
                    UserWallet wallet = UserWallet.builder()
                            .userId(sub)
                            .fullName(finalName)
                            .email(email)
                            .availablePoints(0L)
                            .build();
                    walletRepo.save(wallet);

                    return savedUser;
                });
    }
}