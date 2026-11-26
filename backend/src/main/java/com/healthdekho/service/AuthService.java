package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.CustomerDto;
import com.healthdekho.dto.ApiDtos.LoginRequest;
import com.healthdekho.dto.ApiDtos.LoginResponse;
import com.healthdekho.entity.Customer;
import com.healthdekho.exception.ApiException;
import com.healthdekho.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {
    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long tokenHours;

    public AuthService(CustomerRepository customers, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       @Value("${app.token-hours:12}") long tokenHours) {
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.tokenHours = tokenHours;
    }

    public LoginResponse login(LoginRequest request) {
        Customer customer = customers.findByEmailIgnoreCase(request.email().trim())
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .filter(c -> passwordEncoder.matches(request.password(), c.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect."));
        Instant now = Instant.now();
        Instant expires = now.plus(tokenHours, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("healthdekho").subject(customer.getEmail())
                .issuedAt(now).expiresAt(expires).claim("customerId", customer.getId()).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        CustomerDto profile = new CustomerDto(customer.getId(), customer.getEmail(), customer.getFullName(), customer.getDateOfBirth(), customer.getPhone());
        return new LoginResponse(token, "Bearer", tokenHours * 3600, profile);
    }

    public CustomerDto me(String email) {
        Customer customer = customers.findByEmailIgnoreCase(email).orElseThrow(() -> ApiException.notFound("Customer profile was not found."));
        return new CustomerDto(customer.getId(), customer.getEmail(), customer.getFullName(), customer.getDateOfBirth(), customer.getPhone());
    }
}
