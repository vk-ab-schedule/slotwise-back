package ru.vk.itmo.scheduler.slotwise.api;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
    @Autowired
    private WebApplicationContext context;
    protected MockMvc mvc;

    @BeforeEach
    void authenticatedContractClient() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").with(user("contract-reviewer")
                        .roles("ADMIN", "ORGANIZER", "TEACHER", "STUDENT")).with(csrf()))
                .apply(springSecurity()).build();
    }
}
