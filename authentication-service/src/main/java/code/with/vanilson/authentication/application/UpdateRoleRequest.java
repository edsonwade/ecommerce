package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull(message = "role must not be null") Role role) {

}
