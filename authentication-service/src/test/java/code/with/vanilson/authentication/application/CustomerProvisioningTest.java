package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.infrastructure.CustomerRegistrationClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerProvisioning")
class CustomerProvisioningTest {

    @Mock CustomerRegistrationClient client;
    @InjectMocks CustomerProvisioning provisioning;

    private final User user = User.builder()
            .id(9L).firstname("Rui").lastname("Costa").email("rui@x.com")
            .role(Role.USER).tenantId("default").password("h").build();

    @Nested @DisplayName("syncCustomerProfile")
    class Sync {
        @Test
        void forwards_identity_to_internal_update() {
            provisioning.syncCustomerProfile(user);
            verify(client).updateCustomer(eq("9"),
                    any(CustomerRegistrationClient.CustomerRegistrationRequest.class));
        }

        @Test
        void is_fail_open_when_customer_service_is_down() {
            doThrow(new RuntimeException("connection refused"))
                    .when(client).updateCustomer(any(), any());
            assertThatCode(() -> provisioning.syncCustomerProfile(user))
                    .doesNotThrowAnyException();
        }
    }

    @Nested @DisplayName("deleteCustomerProfile")
    class Delete {
        @Test
        void forwards_delete_to_internal_endpoint() {
            provisioning.deleteCustomerProfile(9L);
            verify(client).deleteCustomer("9");
        }

        @Test
        void is_fail_open_when_customer_service_is_down() {
            doThrow(new RuntimeException("boom")).when(client).deleteCustomer("9");
            assertThatCode(() -> provisioning.deleteCustomerProfile(9L))
                    .doesNotThrowAnyException();
        }
    }
}
