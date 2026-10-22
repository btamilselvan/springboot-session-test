package com.example.sessionleak.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, Object> hello(Authentication authentication, HttpServletRequest request) {
        // getSession(false) never creates one -- unlike injecting HttpSession
        // directly, which would force Tomcat to create a session on every
        // request and silently defeat SessionCreationPolicy.STATELESS.
        HttpSession session = request.getSession(false);

        Map<String, Object> body = new HashMap<>();
        body.put("principal", authentication.getName());
        body.put("authorityCount", authentication.getAuthorities().size());
        body.put("sessionId", session != null ? session.getId() : null);
        return body;
    }
}
