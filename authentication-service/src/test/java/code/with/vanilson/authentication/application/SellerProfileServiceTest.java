package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SellerProfileServiceTest — unit tests for reading and merging the seller business profile.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SellerProfileService Unit Tests")
class SellerProfileServiceTest {

    @Mock
    private UserRepository userRepo;

    @InjectMocks
    private SellerProfileService service;

    private User seller(Long id) {
        return User.builder()
                .id(id)
                .firstname("Vanilson")
                .lastname("Muhongo")
                .email("seller@shop.com")
                .role(Role.SELLER)
                .companyName("Vamuhong Lda")
                .vatNumber("PT123456789")
                .street("Av. da Liberdade 10")
                .city("Lisboa")
                .country("Portugal")
                .postalCode("1250-001")
                .build();
    }

    @Nested
    @DisplayName("getSellerProfile()")
    class GetSellerProfile {

        @Test
        @DisplayName("returns the full business identity for an existing seller")
        void returnsProfile() {
            when(userRepo.findById(5L)).thenReturn(Optional.of(seller(5L)));

            SellerProfileResponse res = service.getSellerProfile(5L);

            assertThat(res.id()).isEqualTo(5L);
            assertThat(res.fullName()).isEqualTo("Vanilson Muhongo");
            assertThat(res.email()).isEqualTo("seller@shop.com");
            assertThat(res.companyName()).isEqualTo("Vamuhong Lda");
            assertThat(res.vatNumber()).isEqualTo("PT123456789");
            assertThat(res.city()).isEqualTo("Lisboa");
            assertThat(res.country()).isEqualTo("Portugal");
        }

        @Test
        @DisplayName("throws when the seller does not exist")
        void throwsWhenMissing() {
            when(userRepo.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSellerProfile(99L))
                    .isInstanceOf(AuthUserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateMyProfile()")
    class UpdateMyProfile {

        @Test
        @DisplayName("merges only non-blank fields, leaving others untouched")
        void mergesNonBlank() {
            User existing = seller(7L);
            when(userRepo.findById(7L)).thenReturn(Optional.of(existing));
            when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Only company + VAT supplied; blanks must not overwrite city/country.
            SellerProfileRequest req = new SellerProfileRequest(
                    "New Company SA", "PT999888777", "  ", null, "", null);

            SellerProfileResponse res = service.updateMyProfile(7L, req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepo).save(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.getCompanyName()).isEqualTo("New Company SA");
            assertThat(saved.getVatNumber()).isEqualTo("PT999888777");
            // Untouched because request values were blank/null:
            assertThat(saved.getCity()).isEqualTo("Lisboa");
            assertThat(saved.getCountry()).isEqualTo("Portugal");
            assertThat(res.companyName()).isEqualTo("New Company SA");
        }

        @Test
        @DisplayName("throws when the user does not exist")
        void throwsWhenMissing() {
            when(userRepo.findById(123L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateMyProfile(123L,
                    new SellerProfileRequest("X", null, null, null, null, null)))
                    .isInstanceOf(AuthUserNotFoundException.class);
        }
    }
}
