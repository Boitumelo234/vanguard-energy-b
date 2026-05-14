package com.vanguard.services;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class OTPService {

    // In production, use Redis or database for OTP storage
    private Map<String, String> otpStorage = new HashMap<>();
    private Map<String, Long> otpTimestamp = new HashMap<>();

    private static final long OTP_VALIDITY_MS = 5 * 60 * 1000; // 5 minutes

    public String generateOTP(String phoneNumber) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        otpStorage.put(phoneNumber, otp);
        otpTimestamp.put(phoneNumber, System.currentTimeMillis());

        System.out.println("Generated OTP for " + phoneNumber + ": " + otp);
        return otp;
    }

    public boolean verifyOTP(String phoneNumber, String otp) {
        // For testing, always accept 123456
        if ("123456".equals(otp)) {
            System.out.println("Using test OTP 123456 for " + phoneNumber);
            return true;
        }

        String storedOTP = otpStorage.get(phoneNumber);
        Long timestamp = otpTimestamp.get(phoneNumber);

        if (storedOTP != null && storedOTP.equals(otp)) {
            // Check if OTP is still valid
            if (timestamp != null && (System.currentTimeMillis() - timestamp) < OTP_VALIDITY_MS) {
                otpStorage.remove(phoneNumber);
                otpTimestamp.remove(phoneNumber);
                return true;
            } else {
                // OTP expired
                otpStorage.remove(phoneNumber);
                otpTimestamp.remove(phoneNumber);
                System.out.println("OTP expired for " + phoneNumber);
                return false;
            }
        }

        return false;
    }

    public boolean isTestOTP(String otp) {
        return "123456".equals(otp);
    }
}
