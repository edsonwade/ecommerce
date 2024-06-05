package code.with.vanilson;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.springframework.validation.annotation.Validated;

@Embeddable
@Validated
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class Address {

    private String street;
    @Column(name = "house_number")
    private String houseNumber;
    @Column(name = "zip_number", length = 8)
    private String zipCode;
    private String country;
    private String city;
}
