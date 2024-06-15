package code.with.vanilson.customerservice;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends MongoRepository<Customer, String> {
    Optional<Customer> findCustomerByEmail(String email);
    Optional<Customer> findByCustomerId(String customerId);
}
