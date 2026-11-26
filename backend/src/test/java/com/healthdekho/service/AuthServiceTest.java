package com.healthdekho.service;

import com.healthdekho.dto.ApiDtos.LoginRequest;
import com.healthdekho.entity.Customer;
import com.healthdekho.exception.ApiException;
import com.healthdekho.repository.CustomerRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final String SECRET = "unit-test-secret-that-is-at-least-32-chars";
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);   // low cost = fast tests
    private final SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @Mock CustomerRepository customers;
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(customers, encoder, new NimbusJwtEncoder(new ImmutableSecret<>(key)), 12);
    }

    private Customer customer(String status) {
        Customer c = TestData.customer("aarav@example.com", LocalDate.of(1990, 4, 15));
        ReflectionTestUtils.setField(c, "passwordHash", encoder.encode("secret-pass"));
        ReflectionTestUtils.setField(c, "status", status);
        return c;
    }

    @Test
    void validLoginReturnsATokenThatNamesTheCustomer() {
        when(customers.findByEmailIgnoreCase("aarav@example.com")).thenReturn(Optional.of(customer("ACTIVE")));

        var response = service.login(new LoginRequest(" aarav@example.com ", "secret-pass"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(12 * 3600);
        assertThat(response.customer().email()).isEqualTo("aarav@example.com");
        // The token must be readable with the same secret and carry the email as its subject.
        var jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(response.token());
        assertThat(jwt.getSubject()).isEqualTo("aarav@example.com");
    }

    @Test
    void wrongPasswordIsRejected() {
        when(customers.findByEmailIgnoreCase("aarav@example.com")).thenReturn(Optional.of(customer("ACTIVE")));

        assertThatThrownBy(() -> service.login(new LoginRequest("aarav@example.com", "wrong")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void unknownEmailIsRejectedWithTheSameError() {
        when(customers.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "secret-pass")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void inactiveCustomerCannotLogIn() {
        when(customers.findByEmailIgnoreCase("aarav@example.com")).thenReturn(Optional.of(customer("SUSPENDED")));

        assertThatThrownBy(() -> service.login(new LoginRequest("aarav@example.com", "secret-pass")))
                .isInstanceOf(ApiException.class);
    }
}
