package com.devflow.health;
import com.devflow.health.controller.HealthController;
import com.devflow.health.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(HealthController.class)
class HealthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HealthService service;
    @Test void returnsUp() throws Exception {
        when(service.isHealthy()).thenReturn(true);
        mvc.perform(get("/api/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.database").value("UP"));
    }
    @Test void returnsUnavailable() throws Exception {
        when(service.isHealthy()).thenReturn(false);
        mvc.perform(get("/api/health")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("DOWN")).andExpect(jsonPath("$.database").value("DOWN"));
    }
}
