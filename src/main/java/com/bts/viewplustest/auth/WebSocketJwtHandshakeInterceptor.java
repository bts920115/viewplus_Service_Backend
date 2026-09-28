package com.bts.viewplustest.auth;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtService jwt;

    public WebSocketJwtHandshakeInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes) {
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null && request instanceof org.springframework.http.server.ServletServerHttpRequest servlet) {
            var session = servlet.getServletRequest().getSession(false);
            String origin = request.getHeaders().getOrigin();
            String expected = request.getURI().getScheme() + "://" + request.getURI().getAuthority();
            if (session != null && expected.equals(origin)) {
                var context = session.getAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                if (context instanceof org.springframework.security.core.context.SecurityContext security &&
                    security.getAuthentication() != null &&
                    security.getAuthentication().getPrincipal() instanceof Long userId) {
                    attributes.put("userId", userId);
                    attributes.put("httpSessionId", session.getId());
                    return true;
                }
            }
        }
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        if (token == null && request.getURI().getQuery() != null)
            for (String part : request.getURI().getQuery().split("&")) {
                if (part.startsWith("access_token=")) {
                    token = java.net.URLDecoder.decode(part.substring(13), java.nio.charset.StandardCharsets.UTF_8);
                    break;
                }
            }
        if (token == null) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            attributes.put("userId", jwt.userId(token));
            return true;
        } catch (RuntimeException e) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) {
    }
}
