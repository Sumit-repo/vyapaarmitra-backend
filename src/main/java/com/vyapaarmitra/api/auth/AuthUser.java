package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.user.Role;
import java.util.UUID;

/**
 * Authenticated principal carried in the security context. Built purely from the
 * access token so request handling stays DB-free until a service needs data.
 */
public record AuthUser(UUID id, UUID businessId, Role role) {
}
