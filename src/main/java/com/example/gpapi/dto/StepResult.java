package com.example.gpapi.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
public class StepResult {

    /** 행 배경색 결정용 — LogEventBus 가 발행 시점에 스탬프 */
    public enum Mode {
        TEST,         // TEST 모드 진행 중 (회색)
        PROD,         // 운영 모드 진행 중 (하늘색)
        MODE_CHANGE   // 모드 전환 이벤트 (노랑)
    }

    private final LocalDateTime timestamp;
    private final String step;
    private final boolean success;
    private final String message;

    @Setter
    private Mode mode;

    public StepResult(LocalDateTime timestamp, String step, boolean success, String message) {
        this.timestamp = timestamp;
        this.step = step;
        this.success = success;
        this.message = message;
    }

    public static StepResult success(String step, String message) {
        return new StepResult(LocalDateTime.now(), step, true, message);
    }

    public static StepResult fail(String step, String message) {
        return new StepResult(LocalDateTime.now(), step, false, message);
    }
}
