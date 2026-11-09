package br.com.jamorder.security;

import br.com.jamorder.user.JdbcLocalUserRepository;
import br.com.jamorder.user.LocalUser;
import br.com.jamorder.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcLocalUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String username;

    @BeforeEach
    void createUser() {
        username = "security-" + UUID.randomUUID();
        userRepository.insert(new LocalUser(null, username, passwordEncoder.encode("correct-password"), "Security Test", UserRole.REQUESTER, true));
    }

    @Test
    void authenticatesValidLocalUser() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", "correct-password"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("REQUESTER"));
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", "incorrect-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsProtectedEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/purchase-requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void providesCsrfTokenWithoutAuthenticationForTheLoginForm() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void buyerCannotCreatePurchaseRequest() throws Exception {
        mockMvc.perform(post("/api/purchase-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void buyerCannotManageUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void onlyAdminCanSeeTrash() throws Exception {
        mockMvc.perform(get("/api/purchase-requests/trash"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/trash.html"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanSeeTrash() throws Exception {
        mockMvc.perform(get("/api/purchase-requests/trash"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "REQUESTER")
    void requesterCannotRestoreFromTrash() throws Exception {
        mockMvc.perform(post("/api/purchase-requests/1/restore").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REQUESTER")
    void requesterNeedsCsrfTokenToCreatePurchaseRequest() throws Exception {
        mockMvc.perform(post("/api/purchase-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REQUESTER")
    void requesterCanCreatePurchaseRequestWithCsrfToken() throws Exception {
        mockMvc.perform(post("/api/purchase-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated());
    }

    private String validRequestBody() {
        return """
                {"requestNumber":"REQ-%s","department":"Infrastructure","requestedOn":"2026-09-23","totvsRequestedOn":"2026-09-23","purchaseReason":"Security test request.","priority":"HIGH","items":[{"description":"Network switch","quantity":1,"totalValue":1200.00}]}
                """.formatted(UUID.randomUUID());
    }
}
