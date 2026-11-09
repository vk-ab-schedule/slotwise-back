package ru.vk.itmo.scheduler.slotwise.api;

import org.mockito.Mockito;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningAvailabilityService;
import ru.vk.itmo.scheduler.slotwise.auth.AccountPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.vk.itmo.scheduler.slotwise.account.AccountRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * MVC/security contract boundary; real persistence and login have separate integration tests.
 */
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
public abstract class ApiContractTestBase {
    @MockitoBean
    private AccountRepository accounts;
    @MockitoBean
    protected PlanningAvailabilityService availability;
    @Autowired
    private WebApplicationContext context;
    protected MockMvc mvc;

    @BeforeEach
    void authenticatedContractClient() {
        var principal = Mockito.mock(AccountPrincipal.class);
        Mockito.when(principal.getAccountId()).thenReturn(1L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, AuthorityUtils.createAuthorityList(
                        "ROLE_ADMIN", "ROLE_ORGANIZER", "ROLE_TEACHER", "ROLE_STUDENT"));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").with(SecurityMockMvcRequestPostProcessors.authentication(authentication)).with(csrf()))
                .apply(springSecurity()).build();
    }
}
