package ru.vk.itmo.scheduler.slotwise.account.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.AccountResponse;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.CreateAccountRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountEnabledRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountPasswordRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountRoleRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.UpdateAccountRequest;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;

@RestController
@RequestMapping(value = ApiPaths.ACCOUNTS, produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Accounts", description = "Administrative account management; secrets are never returned")
public class AccountController {
    @GetMapping
    @Operation(operationId = "listAccounts", summary = "Find accounts",
            description = "ADMIN. Search by name or email; optionally filter by role and enabled status.")
    public PageResponse<AccountResponse> listAccounts(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "role", required = false) AccountRole role,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        throw ContractStub.notImplemented();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createAccount", summary = "Create an account",
            description = "ADMIN. Creates an enabled account without a password. Email must be unique; "
                    + "set the password through the dedicated operation.")
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAccount", summary = "Get an account", description = "ADMIN.")
    public AccountResponse getAccount(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "updateAccount", summary = "Update the account profile",
            description = "ADMIN. Replaces all editable profile fields; externalId and patronymic may be null. "
                    + "Role, enabled status and password have dedicated operations. A stale version returns 409.")
    public AccountResponse updateAccount(@PathVariable("id") @Min(1) long id,
                                         @Valid @RequestBody UpdateAccountRequest request) {
        throw ContractStub.notImplemented();
    }

    @PutMapping("/{id}/role")
    @Operation(operationId = "setAccountRole", summary = "Change the account role",
            description = "ADMIN. Ends the account's active login sessions. Changing one's own role or leaving "
                    + "the system without an active administrator is forbidden. A stale version returns 409.")
    public AccountResponse setAccountRole(@PathVariable("id") @Min(1) long id,
                                          @Valid @RequestBody SetAccountRoleRequest request) {
        throw ContractStub.notImplemented();
    }

    @PutMapping("/{id}/enabled")
    @Operation(operationId = "setAccountEnabled", summary = "Enable or disable the account",
            description = "ADMIN. Ends the account's active login sessions. The last active administrator "
                    + "cannot be disabled. A stale version returns 409.")
    public AccountResponse setAccountEnabled(@PathVariable("id") @Min(1) long id,
                                             @Valid @RequestBody SetAccountEnabledRequest request) {
        throw ContractStub.notImplemented();
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "setAccountPassword", summary = "Set or reset the account password",
            description = "ADMIN. Accepts the new password as input only; never returns a password or its hash. "
                    + "A stale version returns 409.")
    public void setAccountPassword(@PathVariable("id") @Min(1) long id,
                                   @Valid @RequestBody SetAccountPasswordRequest request) {
        throw ContractStub.notImplemented();
    }
}
