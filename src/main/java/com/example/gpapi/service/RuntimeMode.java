package com.example.gpapi.service;

import com.example.gpapi.dto.StepResult;
import com.example.gpapi.event.LogEventBus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 통신 대상 토글.
 *  - TEST 모드 (default true) : Mock 프로그램 (HTTP localhost:8090)으로 전송
 *  - 운영 모드               : 실제 GP (WndBroker_GP) 로 WM_COPYDATA 전송
 *
 * GP Program이 환경에 없는 개발 PC에서는 TEST 모드를 기본값으로 둔다.
 */
@Component
public class RuntimeMode {

    private final AtomicBoolean testMode = new AtomicBoolean(true);
    private final LogEventBus eventBus;

    @Autowired
    public RuntimeMode(LogEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public boolean isTestMode() {
        return testMode.get();
    }

    public void setTestMode(boolean enabled) {
        boolean prev = testMode.getAndSet(enabled);
        if (prev != enabled) {
            eventBus.publishStep(StepResult.success(
                    "통신 모드 변경",
                    enabled
                            ? "TEST 모드 ON — Mock 프로그램(HTTP)으로 전송"
                            : "TEST 모드 OFF — 골드넷(GP, WM_COPYDATA)으로 전송"));
        }
    }
}
