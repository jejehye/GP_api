package com.example.gpapi.event;

import com.example.gpapi.dto.RequestLog;
import com.example.gpapi.dto.StepResult;
import com.example.gpapi.service.RuntimeMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class LogEventBus {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 모드 전환 단계의 라벨 — 이 라벨로 들어오면 MODE_CHANGE 로 스탬프 */
    private static final String MODE_CHANGE_STEP = "통신 모드 변경";

    private final List<Consumer<StepResult>> stepListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<RequestLog>> requestListeners = new CopyOnWriteArrayList<>();

    /** RuntimeMode 가 LogEventBus 를 의존하므로 순환 해소를 위해 @Lazy */
    private final RuntimeMode runtimeMode;

    @Autowired
    public LogEventBus(@Lazy RuntimeMode runtimeMode) {
        this.runtimeMode = runtimeMode;
    }

    public void onStep(Consumer<StepResult> listener) {
        stepListeners.add(listener);
    }

    public void onRequest(Consumer<RequestLog> listener) {
        requestListeners.add(listener);
    }

    public void publishStep(StepResult result) {
        // 모드 스탬프 — UI 렌더러가 행 배경색 결정에 사용
        if (result.getMode() == null) {
            if (MODE_CHANGE_STEP.equals(result.getStep())) {
                result.setMode(StepResult.Mode.MODE_CHANGE);
            } else {
                result.setMode(runtimeMode.isTestMode()
                        ? StepResult.Mode.TEST
                        : StepResult.Mode.PROD);
            }
        }

        System.out.printf("[%s] %s | %s | %s | %s%n",
                result.getTimestamp().format(TIME),
                result.getStep(),
                result.isSuccess() ? "성공" : "실패",
                result.getMode(),
                result.getMessage());
        stepListeners.forEach(l -> l.accept(result));
    }

    public void publishRequest(RequestLog log) {
        System.out.printf("[%s] REQUEST | %s | account=%s | pw=%s%n",
                log.timestamp().format(TIME),
                log.success() ? "성공" : "실패",
                log.maskedAccount(),
                log.maskedPw());
        requestListeners.forEach(l -> l.accept(log));
    }
}
