package com.gameexpert.ws.handler;

import com.gameexpert.ws.NicknameHandshakeInterceptor;
import com.gameexpert.ws.WorldBroadcaster;
import com.gameexpert.ws.WorldSessionRegistry;
import com.gameexpert.ws.WsMessageContext;
import com.gameexpert.ws.dto.OnlineUsersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OnlineUsersWsHandler implements WsMessageHandler {
    private final WorldSessionRegistry registry;
    private final WorldBroadcaster broadcaster;

    @Override
    public String type() {
        return "onlineUsers";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        // TODO Lv 15: 현재 월드의 열린 연결에서 닉네임을 조회하고 요청자에게 응답합니다.
        List<String> users = registry.entries(context.worldId()).stream()
                .map(e -> e.session())
                .filter(s -> s.isOpen())
                .map(s -> (String) s.getAttributes().get(NicknameHandshakeInterceptor.ATTR_NICKNAME))
                .sorted().toList();

        broadcaster.sendTo(context.session(), new OnlineUsersResponse(users, users.size()));
    }
}
