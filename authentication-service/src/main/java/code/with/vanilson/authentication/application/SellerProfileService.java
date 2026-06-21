package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * SellerProfileService — reads and updates the seller business profile.
 * <p>
 * The profile is the legal "sold by" identity shown on an order invoice. Reads are public to
 * any authenticated user (a buyer must see who they bought from); updates are restricted by
 * the controller to the authenticated user editing their own record.
 * </p>
 *
 * @author vamuhong
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerProfileService {

    private final UserRepository userRepo;

    /**
     * Returns the public business profile for a seller (any user id). Business fields may be
     * null if the seller hasn't completed their profile — the response then surfaces just the
     * name and email.
     */
    @Transactional(readOnly = true)
    public SellerProfileResponse getSellerProfile(Long sellerId) {
        User user = userRepo.findById(sellerId)
                .orElseThrow(() -> new AuthUserNotFoundException(
                        "Seller " + sellerId + " not found", "auth.user.not.found"));
        return toResponse(user);
    }

    /**
     * Merges the supplied business fields into the authenticated user's record. Only non-blank
     * values are applied, so a partial update never clears existing data.
     */
    @Transactional
    public SellerProfileResponse updateMyProfile(Long userId, SellerProfileRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AuthUserNotFoundException(
                        "User " + userId + " not found", "auth.user.not.found"));

        if (StringUtils.hasText(request.companyName())) user.setCompanyName(request.companyName());
        if (StringUtils.hasText(request.vatNumber()))   user.setVatNumber(request.vatNumber());
        if (StringUtils.hasText(request.street()))      user.setStreet(request.street());
        if (StringUtils.hasText(request.city()))        user.setCity(request.city());
        if (StringUtils.hasText(request.country()))     user.setCountry(request.country());
        if (StringUtils.hasText(request.postalCode()))  user.setPostalCode(request.postalCode());

        userRepo.save(user);
        log.info("[SellerProfile] Business profile updated for userId={}", userId);
        return toResponse(user);
    }

    private SellerProfileResponse toResponse(User u) {
        return new SellerProfileResponse(
                u.getId(),
                u.getFullName(),
                u.getFirstname(),
                u.getLastname(),
                u.getEmail(),
                u.getCompanyName(),
                u.getVatNumber(),
                u.getStreet(),
                u.getCity(),
                u.getCountry(),
                u.getPostalCode()
        );
    }
}
