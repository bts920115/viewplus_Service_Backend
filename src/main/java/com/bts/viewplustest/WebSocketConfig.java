package com.bts.viewplustest;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.bts.viewplustest.auth.WebSocketJwtHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig
        implements WebSocketConfigurer {

    private final SignalingHandler signalingHandler;
    private final WebSocketJwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(
            SignalingHandler signalingHandler, WebSocketJwtHandshakeInterceptor jwtHandshakeInterceptor) {

        this.signalingHandler =
                signalingHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry) {
        /**
         * 현재는 Tailscale 내부 테스트를 위해 Origin을 제한하지 않는다.
         * 공개 배포 시에는 서비스 도메인만 허용하도록 반드시 좁혀야 한다.
         */
        registry
                .addHandler(
                        signalingHandler,
                        "/ws"
                )
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
