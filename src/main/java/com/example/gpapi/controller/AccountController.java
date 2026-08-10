package com.example.gpapi.controller;

import com.example.gpapi.dto.AccountRequest;
import com.example.gpapi.dto.RequestLog;
import com.example.gpapi.event.LogEventBus;
import com.example.gpapi.service.GpAgentService;
import com.example.gpapi.service.GmshAccountService;
import com.example.gpapi.service.RuntimeMode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/send/v1")
public class AccountController {

    private final GpAgentService gpAgentService;
    private final GmshAccountService gmshAccountService;
    private final LogEventBus eventBus;
    private final RuntimeMode runtimeMode;

    private volatile AccountRequest lastRequest;

    public AccountController(GpAgentService gpAgentService,
                             GmshAccountService gmshAccountService,
                             LogEventBus eventBus,
                             RuntimeMode runtimeMode) {
        this.gpAgentService = gpAgentService;
        this.gmshAccountService = gmshAccountService;
        this.eventBus = eventBus;
        this.runtimeMode = runtimeMode;
    }

    public AccountRequest getLastRequest() { return lastRequest; }

    @PostMapping("/account")
    public ResponseEntity<?> sendAccount(@RequestBody AccountRequest request) {
        this.lastRequest = request;

        boolean success = false;
        String errMsg = null;
        try {
            gpAgentService.sendAccount(request.getAccount(), request.getPw());
            gmshAccountService.setAccountInfo(request.getAccount(), request.getPw());
            success = true;
        } catch (Exception e) {
            errMsg = e.getMessage() == null ? "unknown error" : e.getMessage();
        }

        int pwLen = request.getPw() == null ? 0 : request.getPw().length();
        eventBus.publishRequest(new RequestLog(
                LocalDateTime.now(),
                request.getAccount() == null ? "" : request.getAccount(),
                pwLen,
                success,
                errMsg
        ));

        boolean testMode = runtimeMode.isTestMode();
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "result", "success",
                    "mode", testMode ? "TEST" : "PROD"
            ));
        }
        return ResponseEntity.internalServerError().body(Map.of(
                "result", "fail",
                "mode", testMode ? "TEST" : "PROD",
                "message", errMsg
        ));
    }
}
