package com.example.gpapi.service;

// TEST 모드 기능은 운영 빌드에서 사용하지 않도록 전체 주석 처리했습니다.
// 과거 구현 참고용으로만 보존합니다.
//
// import com.example.gpapi.dto.StepResult;
// import com.example.gpapi.event.LogEventBus;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Component;
//
// import java.util.concurrent.atomic.AtomicBoolean;
//
// @Component
// public class RuntimeMode {
//
//     private final AtomicBoolean testMode = new AtomicBoolean(true);
//     private final LogEventBus eventBus;
//
//     @Autowired
//     public RuntimeMode(LogEventBus eventBus) {
//         this.eventBus = eventBus;
//     }
//
//     public boolean isTestMode() {
//         return testMode.get();
//     }
//
//     public void setTestMode(boolean enabled) {
//         boolean prev = testMode.getAndSet(enabled);
//         if (prev != enabled) {
//             eventBus.publishStep(StepResult.success(
//                     "통신 모드 변경",
//                     enabled
//                             ? "TEST 모드 ON — Mock 프로그램(HTTP)으로 전송"
//                             : "TEST 모드 OFF — 골드넷(GP, WM_COPYDATA)으로 전송"));
//         }
//     }
// }
