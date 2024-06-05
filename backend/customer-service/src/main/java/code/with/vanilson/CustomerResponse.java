package code.with.vanilson;

public record CustomerResponse(
        String customerId,
        String firstname,

        String lastname,

        String email,
        Address address) {
}
