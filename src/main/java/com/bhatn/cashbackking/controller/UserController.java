package com.bhatn.cashbackking.controller;

import com.bhatn.cashbackking.entity.User;
import com.bhatn.cashbackking.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
//@CrossOrigin(origins = "https://feature-initialcommit.dwp81oqt95zeu.amplifyapp.com")
public class UserController {

    private final UserService userService;

    @PostMapping("/sync")
    public ResponseEntity<User> sync(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.syncUser(jwt));
    }
}