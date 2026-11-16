package com.devflow.health;
import com.devflow.health.mapper.HealthMapper;
import com.devflow.health.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class HealthServiceTest {
    private final HealthMapper mapper = mock(HealthMapper.class);
    private final HealthService service = new HealthService(mapper);
    @Test void succeeds() {
        when(mapper.checkConnection()).thenReturn(1);
        assertTrue(service.isHealthy());
    }
    @Test void rejectsUnexpectedResult() {
        when(mapper.checkConnection()).thenReturn(0);
        assertFalse(service.isHealthy());
    }
    @Test void handlesDatabaseFailure() {
        when(mapper.checkConnection()).thenThrow(new DataAccessResourceFailureException("offline"));
        assertFalse(service.isHealthy());
    }
}
