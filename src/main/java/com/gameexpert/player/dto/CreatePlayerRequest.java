package com.gameexpert.player.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class CreatePlayerRequest {

    // TODO Lv 3: 2~12글자의 영문 대소문자, 숫자와 밑줄을 허용하는 검증을 적용합니다.
    @NotBlank(message = "이름 공백 불가")
    @Size(min = 2, max = 12)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "이름 영문 대소문자, 숫자, 밑줄만 허용")
    private final String nickname;

    public CreatePlayerRequest(String nickname) {
        this.nickname = nickname;
    }
}
