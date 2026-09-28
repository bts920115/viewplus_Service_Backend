package com.bts.viewplustest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ICE 서버 목록을 클라이언트에 전달하기 위한 설정이다.
 * TURN 자격 증명은 application.properties에 저장하지 않고 환경 변수로 받는다.
 */
@Component
@ConfigurationProperties(prefix = "webrtc")
public class WebRtcIceProperties {

    private final List<String> stunUrls = new ArrayList<>();
    private final Turn turn = new Turn();

    public List<String> getStunUrls() {
        return stunUrls;
    }

    public Turn getTurn() {
        return turn;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void reportConfiguration() {
        org.slf4j.LoggerFactory.getLogger(WebRtcIceProperties.class).info(
                "WebRTC configuration: iceGroups={}, turnConfigured={}, turnUrlCount={}",
                toIceServers().size(), turn.isConfigured(), turn.getUrls().size());
    }

    public List<Map<String, Object>> toIceServers() {
        // 브라우저와 Android WebRTC가 공통으로 이해하는 iceServers JSON 형태로 변환한다.
        List<Map<String, Object>> iceServers = new ArrayList<>();

        if (!stunUrls.isEmpty()) {
            iceServers.add(Map.of("urls", List.copyOf(stunUrls)));
        }

        if (turn.isConfigured()) {
            Map<String, Object> turnServer = new LinkedHashMap<>();
            turnServer.put("urls", turn.getUrls());
            turnServer.put("username", turn.getUsername());
            turnServer.put("credential", turn.getCredential());
            iceServers.add(turnServer);
        }

        return iceServers;
    }

    public static class Turn {

        private final List<String> urls = new ArrayList<>();
        private String username = "";
        private String credential = "";

        public List<String> getUrls() {
            return urls;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getCredential() {
            return credential;
        }

        public void setCredential(String credential) {
            this.credential = credential;
        }

        private boolean isConfigured() {
            return !urls.isEmpty()
                    && !username.isBlank()
                    && !credential.isBlank();
        }
    }
}
