package code.with.vanilson.customerservice;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.Embedded;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@JsonPropertyOrder(value = {"customerId", "firstname", "lastname", "email", "address"})
@ToString
@EqualsAndHashCode
public class Customer {

    @Id
    private String customerId;
    private String firstname;
    private String lastname;
    @Email(message = "Email is not valid", regexp = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$")
    @Indexed(unique = true)
    private String email;
    @Embedded
    private Address address;
}
