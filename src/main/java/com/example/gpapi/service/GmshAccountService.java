package com.example.gpapi.service;

import com.example.gpapi.dto.StepResult;
import com.example.gpapi.event.LogEventBus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** GMSH 프로그램에 계좌 정보를 WM_COPYDATA로 전달한다. */
@Service
public class GmshAccountService {

    private static final int WM_COPYDATA = 0x004A;
    private static final String GMSH_CLASS_NAME = "GmshMainApp-CLASS";
    private static final long GMSH_DW_DATA = 91005L;

    private final ObjectMapper objectMapper;
    private final LogEventBus eventBus;
    private final boolean windows;

    public GmshAccountService(ObjectMapper objectMapper, LogEventBus eventBus) {
        this.objectMapper = objectMapper;
        this.eventBus = eventBus;
        this.windows = Platform.isWindows();
    }

    public void setAccountInfo(String accountNo, String accountPassword) {
        if (!windows) {
            System.out.println("[GMSH] 비 Windows 환경 — SETACCTINFO 통신 비활성화");
            return;
        }

        HWND hwnd = User32.INSTANCE.FindWindow(GMSH_CLASS_NAME, null);
        if (hwnd == null) {
            eventBus.publishStep(StepResult.fail("GMSH 계좌 송신 실패", "GMSH 프로그램을 찾지 못했습니다."));
            throw new IllegalStateException("GMSH 프로그램을 찾지 못했습니다.");
        }

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("command", "SETACCTINFO");
        payload.put("acct_no", accountNo == null ? "" : accountNo);
        payload.put("acct_pwd", simpleEncryptA(accountPassword, true));

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("GMSH 요청 JSON 생성 실패", e);
        }

        sendCopyData(hwnd, json);
        eventBus.publishStep(StepResult.success("GMSH 계좌 송신 성공", "계좌=" + accountNo));
    }

    private void sendCopyData(HWND targetHwnd, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Memory memory = new Memory(bytes.length);
        memory.write(0, bytes, 0, bytes.length);

        COPYDATASTRUCT cds = new COPYDATASTRUCT();
        cds.dwData = new ULONG_PTR(GMSH_DW_DATA);
        cds.cbData = bytes.length;
        cds.lpData = memory;
        cds.write();

        Native.setLastError(0);
        LRESULT result = User32.INSTANCE.SendMessage(
                targetHwnd,
                WM_COPYDATA,
                new WPARAM(0),
                new LPARAM(Pointer.nativeValue(cds.getPointer()))
        );
        long returnValue = result == null ? 0 : result.longValue();
        int error = Native.getLastError();

        System.out.println("[GMSH] dwData=" + GMSH_DW_DATA
                + ", cbData=" + cds.cbData
                + ", LRESULT=" + returnValue
                + ", GetLastError=" + error);

        if (returnValue == 0) {
            eventBus.publishStep(StepResult.fail(
                    "GMSH 계좌 송신 실패",
                    "LRESULT=0, GetLastError=" + error));
            throw new IllegalStateException("GMSH 계좌 송신 실패: LRESULT=0, GetLastError=" + error);
        }
    }

    static String simpleEncryptA(String value, boolean randomKey) {
        if (value == null) {
            return "";
        }

        char mask = 'K';
        if (randomKey) {
            mask = (char) (mask + ThreadLocalRandom.current().nextInt('Z' - 'K'));
        }

        char[] encrypted = new char[value.length() * 2];
        int next = 0;
        for (int i = 0; i < value.length(); i++) {
            String hex = String.format("%02x", (int) value.charAt(i));
            encrypted[next++] = (char) (hex.charAt(0) ^ mask);
            if (randomKey) {
                encrypted[value.length() * 2 - next] = (char) (hex.charAt(1) ^ mask);
            } else {
                encrypted[next++] = (char) (hex.charAt(1) ^ mask);
            }
        }

        return new String(encrypted) + (randomKey ? String.valueOf(mask) : "");
    }

    public static class COPYDATASTRUCT extends Structure {
        public ULONG_PTR dwData;
        public int cbData;
        public Pointer lpData;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("dwData", "cbData", "lpData");
        }
    }
}
