package com.vanguard.controllers;

import com.vanguard.dto.LoginRequestDTO;
import com.vanguard.entities.User;
import com.vanguard.repositories.UserRepository;
import com.vanguard.services.OTPService;
import com.vanguard.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OTPService otpService;

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");

        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phone number is required"));
        }

        // Generate OTP
        String otp = otpService.generateOTP(phoneNumber);

        // In production, send SMS here
        System.out.println("========================================");
        System.out.println("OTP for " + phoneNumber + ": " + otp);
        System.out.println("========================================");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "OTP sent successfully");
        response.put("otp", otp); // Remove in production

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody LoginRequestDTO loginRequest) {
        String phoneNumber = loginRequest.getPhoneNumber();
        String otp = loginRequest.getOtp();

        System.out.println("Verifying OTP for: " + phoneNumber + " with OTP: " + otp);

        // Check OTP - accept 123456 for testing OR the generated OTP
        boolean isValid = otpService.verifyOTP(phoneNumber, otp) || "123456".equals(otp);

        if (!isValid) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid OTP. Use 123456 for testing."));
        }

        Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
        User user;
        boolean isNewUser = false;

        if (userOpt.isEmpty()) {
            // Create new user - default to CUSTOMER
            user = new User();
            user.setPhoneNumber(phoneNumber);
            user.setUserType("CUSTOMER");
            user.setStatus("ACTIVE");
            user.setName(getDefaultName(phoneNumber));
            user = userRepository.save(user);
            isNewUser = true;
            System.out.println("Created new customer: " + phoneNumber);
        } else {
            user = userOpt.get();
            System.out.println("Existing user found: " + phoneNumber + " with type: " + user.getUserType());

            // Check if user is active
            if (!"ACTIVE".equals(user.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Account is suspended. Please contact support."));
            }
        }

        String token = jwtUtil.generateToken(user.getId().toString(), user.getUserType());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("userType", user.getUserType());
        response.put("userId", user.getId());
        response.put("phoneNumber", user.getPhoneNumber());
        response.put("name", user.getName() != null ? user.getName() : "");
        response.put("isNewUser", isNewUser);

        System.out.println("Login successful for: " + phoneNumber + " -> Redirecting to: " + user.getUserType());

        return ResponseEntity.ok(response);
    }

    private String getDefaultName(String phoneNumber) {
        // Generate a default name based on phone number
        if (phoneNumber.equals("0712345678")) {
            return "Driver User";
        } else if (phoneNumber.equals("0712345679")) {
            return "Admin User";
        } else {
            return "Customer " + phoneNumber.substring(phoneNumber.length() - 4);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
    }
}
