//package com.vanguard.utils;
//
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.stereotype.Component;
//
//@Component
//public class PasswordUtils {
//
//    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
//
//    public String encode(String password) {
//        return encoder.encode(password);
//    }
//
//    public boolean matches(String rawPassword, String encodedPassword) {
//        return encoder.matches(rawPassword, encodedPassword);
//    }
//}