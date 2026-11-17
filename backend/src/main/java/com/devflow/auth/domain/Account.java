package com.devflow.auth.domain;
import java.time.OffsetDateTime;
public record Account(long id, String username, String email, String passwordHash, String role,
                      String status, OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime lastLoginAt) {}
