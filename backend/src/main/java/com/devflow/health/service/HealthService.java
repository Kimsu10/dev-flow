package com.devflow.health.service;

import com.devflow.health.mapper.HealthMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final HealthMapper mapper;

    public HealthService(HealthMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isHealthy() {
        try {
            return mapper.checkConnection() == 1;
        } catch (DataAccessException exception) {
            return false;
        }
    }
}
