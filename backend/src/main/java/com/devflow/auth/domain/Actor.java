package com.devflow.auth.domain;
import java.io.Serializable;
public record Actor(long id, String username, String role) implements Serializable {}
