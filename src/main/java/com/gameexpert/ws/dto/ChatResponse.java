package com.gameexpert.ws.dto;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class ChatResponse {
    private final String type = "chat";
    private final String sender;
    private final String content;
    private final String timestamp;

    // TODO Lv 13: API 명세에 맞게 응답 필드와 생성자를 완성합니다.
    public ChatResponse(String sender, String content, String timestamp) {
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
    }
}
