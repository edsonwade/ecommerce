package code.with.vanilson.customerservice;


import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class Address {

    private String street;
    private String houseNumber;
    private String zipCode;
    private String country;
    private String city;
}
