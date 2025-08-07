package com.portfolio.tracker.controller;

import com.portfolio.tracker.security.JwtUtil;
import com.portfolio.tracker.service.UserDetailsServiceImpl;
import com.portfolio.tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {

    @Autowired
    private AuthenticationManager authManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String password) {
        userService.registerUser(username, password);
        return "User registered successfully";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        // Authenticate the user credentials
        authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        // Load user details by username
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        // Generate JWT token using UserDetails
        return jwtUtil.generateToken(userDetails);
    }
}