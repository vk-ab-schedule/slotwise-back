package ru.vk.itmo.scheduler.slotwise.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityContractTests extends ApiContractTestBase {
    @MockitoBean
    private AuthenticationManager authenticationManager;
    @Autowired
    private WebApplicationContext context;

    @Test
    void logoutInvalidatesTheServerSession() throws Exception {
        var client = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var session = new MockHttpSession();
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "reviewer", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        client.perform(get("/api/sessions").session(session)).andExpect(status().isNotImplemented());
        client.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertTrue(session.isInvalid(), "Logout must invalidate the authenticated server session");
        client.perform(get("/api/sessions")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCsrfIsForbidden() throws Exception {
        var client = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        client.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void authenticationAndRoleFailuresKeepMikhailErrorCodes() throws Exception {
        mvc.perform(get("/api/sessions").with(anonymous())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/admin/accounts").with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/auth/login").with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unexpectedFailuresReturnSanitizedProblemDetails() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new IllegalStateException("internal-test-detail"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.org\",\"password\":\"secret\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Внутренняя ошибка сервера"));
    }

    @Test
    void implementedAuthenticationIsNotDocumentedAsAStub() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['501']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.responses['204'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.responses['403'].content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ApiProblem"))
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.responses['501']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].csrfHeader").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].sessionCookie").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.requestBody").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security[0].csrfHeader").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security[0].sessionCookie").doesNotExist());
    }
}
