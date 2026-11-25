package ru.vk.itmo.scheduler.slotwise.account.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import ru.vk.itmo.scheduler.slotwise.account.AccountService;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.AccountResponse;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.CreateAccountRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountEnabledRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountPasswordRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountRoleRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.UpdateAccountRequest;
import ru.vk.itmo.scheduler.slotwise.auth.AccountPrincipal;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = ApiPaths.ACCOUNTS, produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Accounts", description = "Administrative account management; secrets are never returned")
public class AccountController {
    private final AccountService accounts;

    @GetMapping
    @Operation(operationId = "listAccounts", summary = "Find accounts",
            description = "ADMIN. Search by a part of the full name or email, ignoring case; optionally filter "
                    + "by role and enabled status. Sorted by last name, first name and id.")
    public PageResponse<AccountResponse> listAccounts(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "role", required = false) AccountRole role,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return accounts.find(search, role, enabled, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createAccount", summary = "Create an account",
            description = "ADMIN. Creates an enabled account without a password; set the password through the "
                    + "dedicated operation. Email is stored in lower case and is unique ignoring case. "
                    + "A taken email or externalId returns 409 EMAIL_TAKEN or EXTERNAL_ID_TAKEN with accountId "
                    + "of the existing account.")
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accounts.create(request);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAccount", summary = "Get an account", description = "ADMIN.")
    public AccountResponse getAccount(@PathVariable("id") @Min(1) long id) {
        return accounts.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "updateAccount", summary = "Update the account profile",
            description = "ADMIN. Replaces all editable profile fields; externalId and patronymic may be null. "
                    + "Role, enabled status and password have dedicated operations. A stale version returns "
                    + "409 STALE_ACCOUNT_VERSION; a taken email or externalId is handled as in createAccount.")
    public AccountResponse updateAccount(@PathVariable("id") @Min(1) long id,
                                         @Valid @RequestBody UpdateAccountRequest request) {
        return accounts.update(id, request);
    }

    @PutMapping("/{id}/role")
    @Operation(operationId = "setAccountRole", summary = "Change the account role",
            description = "ADMIN. Ends the account's active login sessions. Changing one's own role is 403 "
                    + "OWN_ROLE_CHANGE; leaving the system without an active administrator is 409 "
                    + "LAST_ACTIVE_ADMIN. A stale version returns 409 STALE_ACCOUNT_VERSION.")
    public AccountResponse setAccountRole(@PathVariable("id") @Min(1) long id,
                                          @Valid @RequestBody SetAccountRoleRequest request,
                                          @AuthenticationPrincipal AccountPrincipal admin) {
        return accounts.setRole(id, admin.getAccountId(), request);
    }

    @PutMapping("/{id}/enabled")
    @Operation(operationId = "setAccountEnabled", summary = "Enable or disable the account",
            description = "ADMIN. Disabling ends the account's active login sessions. The last active "
                    + "administrator cannot be disabled: 409 LAST_ACTIVE_ADMIN. A stale version returns "
                    + "409 STALE_ACCOUNT_VERSION.")
    public AccountResponse setAccountEnabled(@PathVariable("id") @Min(1) long id,
                                             @Valid @RequestBody SetAccountEnabledRequest request) {
        return accounts.setEnabled(id, request);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "setAccountPassword", summary = "Set or reset the account password",
            description = "ADMIN. Accepts the new password as input only; never returns a password or its hash. "
                    + "Ends the account's login sessions except the caller's own; the account version does "
                    + "not change, and hasPassword becomes true. A stale version returns 409 STALE_ACCOUNT_VERSION.")
    public void setAccountPassword(@PathVariable("id") @Min(1) long id,
                                   @Valid @RequestBody SetAccountPasswordRequest request,
                                   @AuthenticationPrincipal AccountPrincipal admin, HttpSession session) {
        accounts.setPassword(id, admin.getAccountId(), session.getId(), request);
    }
}
