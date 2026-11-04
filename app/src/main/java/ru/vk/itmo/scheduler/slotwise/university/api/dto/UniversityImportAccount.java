package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;

public record UniversityImportAccount(
        @NotBlank @Size(max = 255) String externalId,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 100) String firstName,
        @Schema(nullable = true) @Size(max = 100) String patronymic,
        @NotNull AccountRole role,
        @NotBlank @Size(max = 64) String timeZone,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, description = "Optional Spring {algorithm}hash; omitted means login is disabled")
        @Pattern(regexp = "\\{[^}]+}.+", message = "Password hash must have the format {algorithm}hash")
        String passwordHash) {
    @Override
    public String toString() {
        return "UniversityImportAccount[passwordHash=***]";
    }

}
