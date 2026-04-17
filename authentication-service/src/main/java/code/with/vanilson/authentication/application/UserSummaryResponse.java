package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;

public record UserSummaryResponse(
        Long    id,
        String  email,
        String  firstname,
        String  lastname,
        Role    role,
        String  tenantId,
        boolean accountEnabled) {}
