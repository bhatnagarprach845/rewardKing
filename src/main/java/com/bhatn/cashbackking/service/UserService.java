package com.bhatn.cashbackking.service;

import com.bhatn.cashbackking.entity.User;
import com.bhatn.cashbackking.entity.UserWallet;
import com.bhatn.cashbackking.repository.UserRepository;
import com.bhatn.cashbackking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final WalletRepository walletRepo; // Added this

    @Transactional
    public User syncUser(Jwt jwt) {
        String sub = jwt.getClaimAsString("sub");
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        // Safety check for the 'null name' error we saw earlier
        if (name == null) name = email.split("@")[0];

        String finalName = name;
        return userRepo.findById(sub)
                .map(existingUser -> {
                    existingUser.setEmail(email);
                    existingUser.setName(finalName);
                    return userRepo.save(existingUser);
                })
                .orElseGet(() -> {
                    // 1. Create User
                    User newUser = User.builder()
                            .cognitoId(sub)
                            .email(email)
                            .name(finalName)
                            .build();
                    User savedUser = userRepo.saveAndFlush(newUser);

                    // 2. Create Wallet (This prevents the FK error)
                    UserWallet wallet = UserWallet.builder()
                            .userId(sub)
                            .currentBalance(BigDecimal.ZERO)
                            .build();
                    walletRepo.save(wallet);

                    return savedUser;
                });
    }
}