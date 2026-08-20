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

        boolean gpSuccess = false;
        boolean gmshSuccess = false;
        String gpError = null;
        String gmshError = null;

        try {
            gpAgentService.sendAccount(request.getAccount(), request.getPw());
            gpSuccess = true;
        } catch (Exception e) {
            gpError = messageOf(e);
        }

        // GP 전송 결과와 관계없이 GMSH 전송은 반드시 시도한다.
        try {
            gmshAccountService.setAccountInfo(request.getAccount(), request.getPw());
            gmshSuccess = true;
        } catch (Exception e) {
            gmshError = messageOf(e);
        }

        boolean success = gpSuccess && gmshSuccess;
        String errMsg = combinedError(gpError, gmshError);

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
                    "result", "success",
                    "gp", "success",
                    "gmsh", "success"
            ));
        }
        return ResponseEntity.internalServerError().body(Map.of(
                "result", "fail",
                "gp", gpSuccess ? "success" : "fail",
                "gmsh", gmshSuccess ? "success" : "fail",
                "message", errMsg
        ));
    }

    /** 기존 GP 전송과 무관하게 GMSH SETACCTINFO만 실행한다. */
    @PostMapping("/gmsh/account")
    public ResponseEntity<?> sendGmshAccount(@RequestBody AccountRequest request) {
        try {
            gmshAccountService.setAccountInfo(request.getAccount(), request.getPw());
            return successResponse("SETACCTINFO");
        } catch (Exception e) {
            return failureResponse("SETACCTINFO", messageOf(e));
        }
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

    private static String combinedError(String gpError, String gmshError) {
        if (gpError != null && gmshError != null) {
            return "GP: " + gpError + " | GMSH: " + gmshError;
        }
        if (gpError != null) return "GP: " + gpError;
        if (gmshError != null) return "GMSH: " + gmshError;
        return "unknown error";
    }
}
