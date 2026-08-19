package com.example.gpapi.controller;

import com.example.gpapi.dto.AccountRequest;
import com.example.gpapi.dto.ClearAccountRequest;
import com.example.gpapi.dto.OpenScreenRequest;
import com.example.gpapi.dto.RequestLog;
import com.example.gpapi.event.LogEventBus;
import com.example.gpapi.service.GpAgentService;
import com.example.gpapi.service.GmshAccountService;
// TEST 모드 제거 전 코드 보존: import com.example.gpapi.service.RuntimeMode;
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
    // TEST 모드 제거 전 코드 보존: private final RuntimeMode runtimeMode;

    private volatile AccountRequest lastRequest;

    public AccountController(GpAgentService gpAgentService,
                             GmshAccountService gmshAccountService,
                             LogEventBus eventBus) {
                             // TEST 모드 제거 전 파라미터: RuntimeMode runtimeMode
        this.gpAgentService = gpAgentService;
        this.gmshAccountService = gmshAccountService;
        this.eventBus = eventBus;
        // TEST 모드 제거 전 코드 보존: this.runtimeMode = runtimeMode;
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

        // TEST 모드 제거 전 코드 보존:
        // boolean testMode = runtimeMode.isTestMode();
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "result", "success"
            ));
        }
        return ResponseEntity.internalServerError().body(Map.of(
                "result", "fail",
                "message", errMsg
        ));
    }

    @PostMapping("/account/clear")
    public ResponseEntity<?> clearAccount(@RequestBody ClearAccountRequest request) {
        try {
            gmshAccountService.clearAccountInfo(request.getAccount());
            return successResponse("CLEARACCTINFO");
        } catch (Exception e) {
            return failureResponse("CLEARACCTINFO", messageOf(e));
        }
    }

    @PostMapping("/screen/open")
    public ResponseEntity<?> openScreen(@RequestBody OpenScreenRequest request) {
        try {
            gmshAccountService.openScreen(
                    request.getAccount(),
                    request.getPw(),
                    request.getScreenNo(),
                    request.getJcode());
            return successResponse("OPENSCREEN");
        } catch (Exception e) {
            return failureResponse("OPENSCREEN", messageOf(e));
        }
    }

    private ResponseEntity<?> successResponse(String command) {
        return ResponseEntity.ok(Map.of(
                "result", "success",
                "command", command
        ));
    }

    private ResponseEntity<?> failureResponse(String command, String message) {
        return ResponseEntity.internalServerError().body(Map.of(
                "result", "fail",
                "command", command,
                "message", message
        ));
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? "unknown error" : e.getMessage();
    }
}
