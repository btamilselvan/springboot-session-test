package com.example.sessionleak.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;

/**
 * Stand-in for a real-world GrantedAuthority that carries non-trivial
 * metadata (claims, resource lists, etc). The padding makes the per-session
 * heap cost of the leak visible after a few thousand requests instead of
 * requiring millions.
 */
public class FatAuthority implements GrantedAuthority {

    private final String role;
    private final byte[] payload;

    public FatAuthority(String role, int payloadBytes) {
        this.role = role;
        this.payload = new byte[payloadBytes];
        Arrays.fill(this.payload, (byte) 1);
    }

    @Override
    public String getAuthority() {
        return role;
    }
}
