package code.with.vanilson.authentication.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AccountUpdateResponse — result of PATCH /api/v1/auth/account/me.
 * tokens is non-null ONLY when the email changed: the JWT subject is the email, so the
 * caller's current access token died with the old address and it must adopt this pair.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountUpdateResponse(
        AccountResponse account,
        AuthResponse tokens
) {}
