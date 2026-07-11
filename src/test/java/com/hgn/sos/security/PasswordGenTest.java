package com.hgn.sos.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordGenTest {
    @Test
    void gen() {
        System.out.println("HASH: " + new BCryptPasswordEncoder().encode("password"));
    }
}