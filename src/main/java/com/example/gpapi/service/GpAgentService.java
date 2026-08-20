package com.example.gpapi.service;

import com.example.gpapi.dto.StepResult;
import com.example.gpapi.event.LogEventBus;
import com.example.gpapi.util.MaskingUtils;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.WinDef.ATOM;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GP/Mock 과 WM_COPYDATA 100/101/102 흐름.
 *
 * UI 로그는 다음 2개 이벤트만 발행:
 *  - "골드넷 연결 성공" : dwData=101 "ok" 핸드셰이크 ack 수신 시
 *  - "계좌 송신 성공" / "계좌 송신 실패" : dwData=102 송신 LRESULT 에 따라
 *
 * 그 외 진단 정보는 모두 System.out.println 으로만 흘림 (UI 오염 방지).
 */
@Service
public class GpAgentService {

    private static final int WM_COPYDATA = 0x004A;

    private final LogEventBus eventBus;

    private HWND myHwnd;
    private HWND gpHwnd;

    /** WindowProc 콜백 강참조. GC되면 dead pointer로 LRESULT=0. */
    private WindowProc windowProcRef;

    private volatile String currentAccount;
    private volatile String currentPassword;

    /** "골드넷 연결 성공" 이벤트 중복 방지 — 같은 핸들로 이미 연결 알림을 보냈는지 추적 */
    private volatile long lastConnectedPeer = 0L;

    private final boolean windows;

    public GpAgentService(LogEventBus eventBus) {
        this.eventBus = eventBus;
        this.windows = Platform.isWindows();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        if (!windows) {
            System.out.println("[GpAgent] 비 Windows 환경 — GP 통신 비활성화");
            return;
        }
        Thread agentThread = new Thread(this::runAgent, "gp-agent-thread");
        agentThread.setDaemon(true);
        agentThread.start();
    }

    private void runAgent() {
        try {
            createHiddenWindow();
        } catch (Exception e) {
            System.out.println("[GpAgent] 숨은 윈도우 생성 실패: " + e.getMessage());
            return;
        }
        tryConnect();
        Thread reconnectThread = new Thread(this::reconnectLoop, "gp-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();

        MSG msg = new MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
    }

    /** GP 검색 + dwData=100 송신. UI 로그 없음 (성공 시 101 수신에서 "골드넷 연결 성공" 발행됨). */
    private boolean tryConnect() {
        List<Long> peers = findAllGpWindowPeers();
        if (peers.isEmpty()) {
            System.out.println("[GpAgent] GP/Mock 윈도우 미발견 — 재연결 대기");
            gpHwnd = null;
            return false;
        }
        gpHwnd = new HWND(new Pointer(peers.get(0)));
        System.out.println("[GpAgent] GP 윈도우 발견: " + describeWindow(gpHwnd));
        try {
            sendMyHandle();
            return true;
        } catch (Exception e) {
            System.out.println("[GpAgent] dwData=100 송신 실패: " + e.getMessage());
            return false;
        }
    }

    private void reconnectLoop() {
        int prevCount = -1;
        while (true) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }
            List<Long> peers = findAllGpWindowPeers();
            int count = peers.size();
            HWND prev = gpHwnd;
            boolean primaryAlive = (prev != null) && User32.INSTANCE.IsWindow(prev);

            boolean countChanged = (count != prevCount && prevCount != -1);
            boolean needsRefresh = (count > 0) && (!primaryAlive || countChanged);

            if (needsRefresh) {
                gpHwnd = new HWND(new Pointer(peers.get(0)));
                lastConnectedPeer = 0L; // 핸들 갱신 시 연결 알림 재발행 허용
                System.out.println("[GpAgent] GP 재연결 감지: " + describeWindow(gpHwnd));
                try {
                    sendMyHandle();
                } catch (Exception e) {
                    System.out.println("[GpAgent] dwData=100 재송신 실패: " + e.getMessage());
                }
            }
            prevCount = count;
        }
    }

    /** API 컨트롤러 진입점 */
    public void sendAccount(String account, String password) {
        if (!windows) {
            this.currentAccount = account;
            this.currentPassword = password;
            return;
        }

        this.currentAccount = account;
        this.currentPassword = password;

        if (gpHwnd == null || !User32.INSTANCE.IsWindow(gpHwnd)) {
            HWND found = findGpWindow();
            if (found == null) {
                throw new IllegalStateException("골드넷 GP 창을 찾을 수 없습니다.");
            }
            gpHwnd = found;
            try {
                sendMyHandle();
            } catch (Exception e) {
                throw new RuntimeException("HWND 재등록 실패: " + e.getMessage(), e);
            }
        }

        sendAccountJson(account, password);
    }

    private static String mask(String pw) {
        if (pw == null || pw.isEmpty()) return "(빈값)";
        return "*".repeat(pw.length()) + " (" + pw.length() + "자리)";
    }

    private void createHiddenWindow() {
        String className = "JavaHiddenWnd";

        windowProcRef = new WindowProc() {
            @Override
            public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
                return windowProc(hwnd, uMsg, wParam, lParam);
            }
        };

        WNDCLASSEX wc = new WNDCLASSEX();
        wc.cbSize = wc.size();
        wc.lpfnWndProc = windowProcRef;
        wc.lpszClassName = className;

        ATOM result = User32.INSTANCE.RegisterClassEx(wc);
        if (result == null || result.intValue() == 0) {
            throw new IllegalStateException("RegisterClassEx 실패");
        }

        myHwnd = User32.INSTANCE.CreateWindowEx(
                0, className, "JavaHiddenWnd", 0,
                0, 0, 0, 0, null, null, (HINSTANCE) null, null
        );

        if (myHwnd == null) {
            throw new IllegalStateException("CreateWindowEx 실패");
        }

        System.out.println("[GpAgent] 숨은 윈도우 생성: myHwnd=0x"
                + Long.toHexString(Pointer.nativeValue(myHwnd.getPointer())));

        // UIPI 우회
        try {
            User32Ex.INSTANCE.ChangeWindowMessageFilterEx(myHwnd, WM_COPYDATA, MSGFLT_ALLOW, Pointer.NULL);
        } catch (Throwable t) {
            try {
                User32Ex.INSTANCE.ChangeWindowMessageFilter(WM_COPYDATA, MSGFLT_ADD);
            } catch (Throwable ignored) { }
        }
    }

    private LRESULT windowProc(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        if (uMsg == WM_COPYDATA) {
            Pointer raw = new Pointer(lParam.longValue());
            COPYDATASTRUCT cds = new COPYDATASTRUCT(raw);
            cds.read();

            long dwData = cds.dwData.longValue();

            if (dwData == 101) {
                String ack = "";
                if (cds.cbData > 0 && cds.lpData != null) {
                    try {
                        byte[] bytes = cds.lpData.getByteArray(0, cds.cbData);
                        ack = new String(bytes, StandardCharsets.UTF_8).replace("\0", "");
                    } catch (Throwable t) {
                        ack = "(읽기 실패)";
                    }
                }
                System.out.println("[GpAgent] dwData=101 수신, ack=\"" + ack + "\"");

                // "골드넷 연결 성공" UI 이벤트 — 같은 핸들에서 중복 발행 방지
                long peer = (gpHwnd != null) ? Pointer.nativeValue(gpHwnd.getPointer()) : 0L;
                if (peer != 0L && peer != lastConnectedPeer) {
                    lastConnectedPeer = peer;
                    eventBus.publishStep(StepResult.success(
                            "골드넷 연결 성공",
                            describeWindow(gpHwnd)));
                }
            }
            return new LRESULT(1);
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    private static final int MSGFLT_ALLOW = 1;
    private static final int MSGFLT_ADD   = 1;
    public interface User32Ex extends User32 {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean ChangeWindowMessageFilterEx(HWND hWnd, int message, int action, Pointer pChangeFilterStruct);
        boolean ChangeWindowMessageFilter(int message, int dwFlag);
    }

    private HWND findGpWindow() {
        List<Long> peers = findAllGpWindowPeers();
        return peers.isEmpty() ? null : new HWND(new Pointer(peers.get(0)));
    }

    private List<Long> findAllGpWindowPeers() {
        List<Long> peers = new ArrayList<>();
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            char[] cn = new char[256];
            int cLen = User32.INSTANCE.GetClassName(hwnd, cn, 256);
            if (cLen > 0 && "WGToSH".equals(new String(cn, 0, cLen))) {
                char[] tn = new char[256];
                int tLen = User32.INSTANCE.GetWindowText(hwnd, tn, 256);
                if (tLen > 0 && "WndBroker_GP".equals(new String(tn, 0, tLen))) {
                    peers.add(Pointer.nativeValue(hwnd.getPointer()));
                }
            }
            return true;
        }, null);
        return peers;
    }

    private String describeWindow(HWND hwnd) {
        if (hwnd == null) return "hwnd=null";
        char[] cls = new char[256];
        char[] title = new char[256];
        User32.INSTANCE.GetClassName(hwnd, cls, 256);
        User32.INSTANCE.GetWindowText(hwnd, title, 256);
        IntByReference pidRef = new IntByReference();
        int tid = User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
        return "HWND=0x" + Long.toHexString(Pointer.nativeValue(hwnd.getPointer()))
                + ", class=" + Native.toString(cls)
                + ", title=" + Native.toString(title)
                + ", pid=" + pidRef.getValue()
                + ", tid=" + tid;
    }

    /** dwData=100 — HWND 등록. UI 로그 없음 (101 ack 수신에서 "골드넷 연결 성공" 발행). */
    private void sendMyHandle() {
        int hwnd32 = (int) Pointer.nativeValue(myHwnd.getPointer());

        Memory mem = new Memory(4);
        mem.setInt(0, hwnd32);

        COPYDATASTRUCT cds = new COPYDATASTRUCT();
        cds.dwData = new ULONG_PTR(100);
        cds.cbData = 4;
        cds.lpData = mem;
        cds.write();

        // GP 수신 프로그램의 기존 규격: wParam에 전송 데이터 크기를 전달한다.
        WPARAM cbDataParam = new WPARAM(cds.cbData);
        LPARAM cdsPtr = new LPARAM(Pointer.nativeValue(cds.getPointer()));

        List<Long> peers = findAllGpWindowPeers();
        if (peers.isEmpty()) {
            System.out.println("[GpAgent] dwData=100 송신: 대상 윈도우 0개");
            return;
        }
        for (Long peer : peers) {
            HWND target = new HWND(new Pointer(peer));
            Native.setLastError(0);
            LRESULT r = User32.INSTANCE.SendMessage(target, WM_COPYDATA, cbDataParam, cdsPtr);
            int err = Native.getLastError();
            long lr = (r == null) ? -1 : r.longValue();
            System.out.println("[GpAgent] dwData=100 → HWND=0x" + Long.toHexString(peer)
                    + ", LRESULT=" + lr + ", GetLastError=" + err);
        }
    }

    /** dwData=102 — 계좌 JSON 송신. 성공/실패를 UI 로그로 발행. */
    private void sendAccountJson(String account, String password) {
        String json =
                "{"
                        + "\"From\":\"1H\","
                        + "\"type\":\"AC\","
                        + "\"Data\":{"
                        + "\"acct_no\":\"" + account + "\","
                        + "\"acct_pw\":\"" + password + "\""
                        + "},"
                        + "\"Etc\":\"\""
                        + "}";

        byte[] bytes = (json + "\0").getBytes(StandardCharsets.UTF_8);

        Memory mem = new Memory(bytes.length);
        mem.write(0, bytes, 0, bytes.length);

        COPYDATASTRUCT cds = new COPYDATASTRUCT();
        cds.dwData = new ULONG_PTR(102);
        cds.cbData = bytes.length;
        cds.lpData = mem;
        cds.write();

        // GP 수신 프로그램의 기존 규격: wParam에 전송 데이터 크기를 전달한다.
        WPARAM cbDataParam = new WPARAM(cds.cbData);
        LPARAM cdsPtr = new LPARAM(Pointer.nativeValue(cds.getPointer()));

        List<Long> peers = findAllGpWindowPeers();
        if (peers.isEmpty()) {
            eventBus.publishStep(StepResult.fail(
                    "계좌 송신 실패",
                    "GP/Mock 윈도우가 없습니다 — 프로그램이 실행 중인지 확인하세요"));
            throw new IllegalStateException("GP/Mock 윈도우가 없습니다.");
        }

        boolean anySuccess = false;
        String lastErr = null;
        for (Long peer : peers) {
            HWND target = new HWND(new Pointer(peer));
            Native.setLastError(0);
            LRESULT r = User32.INSTANCE.SendMessage(target, WM_COPYDATA, cbDataParam, cdsPtr);
            int err = Native.getLastError();
            long lr = (r == null) ? -1 : r.longValue();
            System.out.println("[GpAgent] dwData=102 → HWND=0x" + Long.toHexString(peer)
                    + ", LRESULT=" + lr + ", GetLastError=" + err
                    + ", bytes=" + bytes.length);
            // 일부 GP 버전은 WM_COPYDATA를 처리하고도 LRESULT를 0으로 반환한다.
            // 대상 HWND가 유효하고 Windows 오류가 없으면 동기 전송 완료로 판단한다.
            if (r != null && err == 0 && User32.INSTANCE.IsWindow(target)) {
                anySuccess = true;
            } else {
                lastErr = "LRESULT=" + lr + ", GetLastError=" + err
                        + (err == 5 ? " (ACCESS_DENIED — UIPI 차단)" : "");
            }
        }

        if (anySuccess) {
            eventBus.publishStep(StepResult.success(
                    "계좌 송신 성공",
                    "계좌=" + MaskingUtils.maskAccount(account) + ", 비밀번호=" + mask(password)));
        } else {
            String msg = "계좌=" + MaskingUtils.maskAccount(account) + ", 비밀번호=" + mask(password)
                    + (lastErr != null ? " | " + lastErr : "");
            eventBus.publishStep(StepResult.fail("계좌 송신 실패", msg));
            throw new RuntimeException("계좌 송신 실패: " + (lastErr == null ? "unknown" : lastErr));
        }
    }

    public static class COPYDATASTRUCT extends Structure {
        public ULONG_PTR dwData;
        public int cbData;
        public Pointer lpData;

        public COPYDATASTRUCT() {}
        public COPYDATASTRUCT(Pointer p) { super(p); }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("dwData", "cbData", "lpData");
        }
    }
}
