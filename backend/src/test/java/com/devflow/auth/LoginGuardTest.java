package com.devflow.auth;

import com.devflow.auth.config.SecurityConfig;
import com.devflow.auth.controller.AuthController;
import com.devflow.auth.service.*;
import com.devflow.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class,LoginRateLimiter.class})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class LoginGuardTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @MockitoBean AccountService accounts;
    @Test void identityLimitReturnsGenericRateStatusBeforeCredentialWork() throws Exception {
        String identity="fixture-"+UUID.randomUUID();
        String password=UUID.randomUUID().toString();
        when(accounts.authenticate(anyString(),anyString())).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid credentials"));
        String body=json.writeValueAsString(Map.of("username",identity,"password",password));
        for(int i=0;i<10;i++) mvc.perform(post("/api/auth/login").with(csrf()).with(q->{q.setRemoteAddr("fixture-peer-"+identity);return q;})
            .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).with(q->{q.setRemoteAddr("fixture-other-"+identity);return q;})
            .contentType("application/json").content(body)).andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("LOGIN_RATE_LIMITED"))
            .andExpect(header().exists("Retry-After"));
        verify(accounts,times(10)).authenticate(anyString(),anyString());
    }
    @Test void peerBudgetPrecedesCsrfAndDoesNotUseUntrustedForwardedHeaders() throws Exception {
        String peer="fixture-peer-"+UUID.randomUUID();
        for(int i=0;i<30;i++) mvc.perform(post("/api/auth/login").with(q->{q.setRemoteAddr(peer);return q;})
            .header("X-Forwarded-For",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(q->{q.setRemoteAddr(peer);return q;}))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value("LOGIN_RATE_LIMITED"))
            .andExpect(header().exists("Retry-After"));
        verifyNoInteractions(accounts);
    }
}
