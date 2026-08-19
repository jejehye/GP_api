package com.example.gpapi.event;

import com.example.gpapi.dto.RequestLog;
import com.example.gpapi.dto.StepResult;
// TEST 모드 제거 전 import 보존:
// import com.example.gpapi.service.RuntimeMode;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class LogEventBus {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // TEST 모드 제거 전 코드 보존:
    // private static final String MODE_CHANGE_STEP = "통신 모드 변경";

    private final List<Consumer<StepResult>> stepListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<RequestLog>> requestListeners = new CopyOnWriteArrayList<>();

    // TEST 모드 제거 전 코드 보존:
    // private final RuntimeMode runtimeMode;
    //
    // @Autowired
    // public LogEventBus(@Lazy RuntimeMode runtimeMode) {
    //     this.runtimeMode = runtimeMode;
    // }

    public void onStep(Consumer<StepResult> listener) {
        stepListeners.add(listener);
    }

    public void onRequest(Consumer<RequestLog> listener) {
        requestListeners.add(listener);
    }

    public void publishStep(StepResult result) {
        // 운영 모드로 고정. 기존 TEST/모드전환 분기는 아래 주석으로 보존한다.
        if (result.getMode() == null) {
            result.setMode(StepResult.Mode.PROD);
            // if (MODE_CHANGE_STEP.equals(result.getStep())) {
            //     result.setMode(StepResult.Mode.MODE_CHANGE);
            // } else {
            //     result.setMode(runtimeMode.isTestMode()
            //             ? StepResult.Mode.TEST
            //             : StepResult.Mode.PROD);
            // }
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
