package code.with.vanilson;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CustomerRequest(
        String customerId,
        @NotNull(message = "Customer firstname is required")
        String firstname,
        @NotNull(message = "Customer lastname is required")
        String lastname,
        @NotNull(message = "Customer email is required")
        @NotEmpty(message = "Email cannot be empty")
        @Email(message = "Customer email is not valid email address")
        String email,
        Address address) {
}
