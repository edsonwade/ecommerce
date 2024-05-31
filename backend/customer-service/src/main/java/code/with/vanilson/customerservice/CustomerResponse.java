package code.with.vanilson.customerservice;

public record CustomerResponse(
        String customerId,
        String firstname,

        String lastname,

        String email,
        Address address) {
}
