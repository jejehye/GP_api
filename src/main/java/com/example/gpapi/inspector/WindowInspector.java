package com.example.gpapi.inspector;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Win32 기반 마우스 호버 윈도우 인스펙터.
 * 200ms 주기로 커서 아래 윈도우의 가능한 모든 식별/구분 속성을 key:value로 표시.
 * Spacebar 또는 F12로 freeze.
 */
public class WindowInspector {

    // 디자인 토큰 (MainFrame 톤과 일관)
    private static final Color BG      = new Color(0xEEF3FB);
    private static final Color SURFACE = Color.WHITE;
    private static final Color TEXT    = new Color(0x0F172A);
    private static final Color MUTED   = new Color(0x64748B);
    private static final Color BORDER  = new Color(0xDBE5F2);
    private static final Color ACCENT  = new Color(0x2563EB);

    // 한글 + 영문 모두 안정적인 폰트 우선
    private static final String[] UI_FONT_CHAIN = {
            "Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo",
            "Noto Sans CJK KR", "Noto Sans KR", "Segoe UI"
    };
    private static final String[] MONO_FONT_CHAIN = {
            "D2Coding", "나눔고딕코딩", "NanumGothicCoding",
            "Sarasa Mono K", "Sarasa Mono SC",
            "Malgun Gothic"  // mono는 아니지만 한글 깨짐 방지용 최후 폴백
    };

    private static final int POLL_MS = 200;
    private static final int MAX_PARENT_DEPTH = 12;

    // GetWindow 플래그
    private static final int GW_OWNER = 4;
    // GetAncestor 플래그
    private static final int GA_PARENT = 1;
    private static final int GA_ROOT = 2;
    private static final int GA_ROOTOWNER = 3;
    // GetWindowLong 인덱스
    private static final int GWL_STYLE = -16;
    private static final int GWL_EXSTYLE = -20;
    private static final int GWL_ID = -12;
    // ChildWindowFromPointEx 플래그
    private static final int CWP_SKIPINVISIBLE = 0x0001;
    private static final int CWP_SKIPDISABLED  = 0x0002;
    private static final int CWP_SKIPTRANSPARENT = 0x0004;
    // 메시지
    private static final int WM_GETTEXT = 0x000D;
    private static final int WM_GETTEXTLENGTH = 0x000E;

    private JFrame frame;
    private JLabel statusLabel;
    private JTextArea infoArea;
    private JButton pauseBtn;
    private JButton copyBtn;

    private Timer pollTimer;
    private volatile boolean paused = false;
    private volatile String lastInfo = "";

    public void show() {
        if (frame == null) buildUi();
        frame.setVisible(true);
        frame.toFront();
        startPolling();
    }

    public void hide() {
        stopPolling();
        if (frame != null) frame.setVisible(false);
    }

    public boolean isShowing() {
        return frame != null && frame.isVisible();
    }

    private void buildUi() {
        frame = new JFrame("Window Inspector — 마우스 호버로 검출");
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setSize(680, 720);
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        statusLabel = new JLabel(" ● 실시간 호버 중 (200ms)");
        statusLabel.setForeground(new Color(0x117A3D));
        statusLabel.setFont(uiFont(12.5f, Font.BOLD));

        pauseBtn = new JButton("정지 (F12)");
        pauseBtn.setFocusPainted(false);
        pauseBtn.setFont(uiFont(12f, Font.PLAIN));
        pauseBtn.putClientProperty("JButton.buttonType", "roundRect");
        pauseBtn.addActionListener(e -> togglePaused());

        copyBtn = new JButton("클립보드 복사");
        copyBtn.setFocusPainted(false);
        copyBtn.setFont(uiFont(12f, Font.PLAIN));
        copyBtn.putClientProperty("JButton.buttonType", "roundRect");
        copyBtn.addActionListener(e -> copyToClipboard());

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setOpaque(false);
        tb.setBorderPainted(false);
        tb.add(statusLabel);
        tb.add(javax.swing.Box.createHorizontalGlue());
        tb.add(pauseBtn);
        tb.add(javax.swing.Box.createHorizontalStrut(6));
        tb.add(copyBtn);
        root.add(tb, BorderLayout.NORTH);

        infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setBackground(SURFACE);
        infoArea.setForeground(TEXT);
        infoArea.setFont(monoFont(12.5f));
        infoArea.setBorder(new EmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(infoArea);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(SURFACE);
        root.add(scroll, BorderLayout.CENTER);

        JLabel hint = new JLabel("팁: Spacebar / F12 = 정지·재개 · 200ms마다 갱신 · 클립보드 복사로 동료에 공유");
        hint.setForeground(MUTED);
        hint.setFont(uiFont(11.5f, Font.PLAIN));
        hint.setBorder(new EmptyBorder(8, 0, 0, 0));
        root.add(hint, BorderLayout.SOUTH);

        frame.setContentPane(root);

        root.registerKeyboardAction(
                e -> togglePaused(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(
                e -> togglePaused(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F12, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { stopPolling(); }
            @Override public void windowDeiconified(WindowEvent e) { startPolling(); }
            @Override public void windowIconified(WindowEvent e) { stopPolling(); }
        });
    }

    private void startPolling() {
        if (pollTimer != null && pollTimer.isRunning()) return;
        pollTimer = new Timer(POLL_MS, e -> tick());
        pollTimer.setCoalesce(true);
        pollTimer.start();
    }

    private void stopPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }
    }

    private void togglePaused() {
        paused = !paused;
        if (paused) {
            statusLabel.setText(" ⏸ 일시 정지 (현재 정보 freeze)");
            statusLabel.setForeground(new Color(0xB45309));
            pauseBtn.setText("재개 (F12)");
        } else {
            statusLabel.setText(" ● 실시간 호버 중 (200ms)");
            statusLabel.setForeground(new Color(0x117A3D));
            pauseBtn.setText("정지 (F12)");
        }
    }

    private void copyToClipboard() {
        if (lastInfo == null || lastInfo.isEmpty()) return;
        StringSelection sel = new StringSelection(lastInfo);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
    }

    private void tick() {
        if (paused) return;
        try {
            POINT pt = new POINT();
            User32.INSTANCE.GetCursorPos(pt);
            POINT.ByValue ptByVal = new POINT.ByValue();
            ptByVal.x = pt.x;
            ptByVal.y = pt.y;
            HWND hwnd = User32Ex.INSTANCE.WindowFromPoint(ptByVal);
            String info = describe(pt, hwnd);
            lastInfo = info;
            infoArea.setText(info);
            infoArea.setCaretPosition(0);
        } catch (Throwable t) {
            infoArea.setText("[Inspector 오류] " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /** 모든 가능한 속성을 key:value 형태로 수집 */
    private String describe(POINT cursor, HWND hwnd) {
        Map<String, String> kv = new LinkedHashMap<>();

        kv.put("Cursor", "(" + cursor.x + ", " + cursor.y + ")");

        if (hwnd == null || Pointer.nativeValue(hwnd.getPointer()) == 0L) {
            kv.put("HWND", "(없음)");
            return formatKv(kv);
        }

        long peer = Pointer.nativeValue(hwnd.getPointer());
        kv.put("HWND", "0x" + Long.toHexString(peer).toUpperCase() + " (decimal " + peer + ")");

        // ── 핵심 식별자 ──
        String cls = getClassName(hwnd);
        String title = getWindowText(hwnd);
        kv.put("Class", cls.isEmpty() ? "(빈 값)" : cls);
        kv.put("Title", title.isEmpty() ? "(빈 값)" : title);

        // ── 텍스트 관련 (WM_GETTEXT가 GetWindowText와 다른 경우 = 사용자 입력값 등) ──
        int textLen = sendIntMessage(hwnd, WM_GETTEXTLENGTH, 0, 0);
        kv.put("Text length", String.valueOf(textLen));
        if (textLen > 0) {
            String wmText = getTextViaMessage(hwnd, textLen);
            if (!wmText.equals(title)) {
                kv.put("Text(WM_GETTEXT)", wmText.isEmpty() ? "(빈 값)" : wmText);
            }
        }

        // ── 컨트롤 ID ──
        int ctrlId = User32Ex.INSTANCE.GetDlgCtrlID(hwnd);
        if (ctrlId != 0) {
            kv.put("ControlID", ctrlId + " (0x" + Integer.toHexString(ctrlId).toUpperCase() + ")");
        }

        // ── 스타일/확장스타일 ──
        // JNA 버전에 따라 GetWindowLongPtr 반환형이 long primitive 또는 LONG_PTR이라 reflection으로 안전 처리
        long style = readLongPtr(hwnd, GWL_STYLE) & 0xFFFFFFFFL;
        long exStyle = readLongPtr(hwnd, GWL_EXSTYLE) & 0xFFFFFFFFL;
        kv.put("Style", "0x" + zeroPad(Long.toHexString(style).toUpperCase(), 8) + "  " + decodeStyle(style));
        kv.put("ExStyle", "0x" + zeroPad(Long.toHexString(exStyle).toUpperCase(), 8) + "  " + decodeExStyle(exStyle));

        // ── 상태 플래그 (스타일 외) ──
        kv.put("Visible", User32.INSTANCE.IsWindowVisible(hwnd) ? "yes" : "no");
        kv.put("Enabled", User32Ex.INSTANCE.IsWindowEnabled(hwnd) ? "yes" : "no");
        kv.put("Iconic", User32Ex.INSTANCE.IsIconic(hwnd) ? "yes (최소화)" : "no");
        kv.put("Zoomed", User32Ex.INSTANCE.IsZoomed(hwnd) ? "yes (최대화)" : "no");
        kv.put("Unicode", User32Ex.INSTANCE.IsWindowUnicode(hwnd) ? "yes" : "no");

        // ── 프로세스/스레드 ──
        IntByReference pidRef = new IntByReference();
        int tid = User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
        int pid = pidRef.getValue();
        kv.put("PID", String.valueOf(pid));
        kv.put("ThreadID", String.valueOf(tid));
        kv.put("Process", getProcessPath(pid));

        // ── 위치/크기 ──
        RECT r = new RECT();
        if (User32.INSTANCE.GetWindowRect(hwnd, r)) {
            kv.put("Rect (screen)", "(" + r.left + "," + r.top + ") - (" + r.right + "," + r.bottom + ") "
                    + "[" + (r.right - r.left) + " x " + (r.bottom - r.top) + "]");
        }
        RECT cr = new RECT();
        if (User32Ex.INSTANCE.GetClientRect(hwnd, cr)) {
            kv.put("ClientRect", "[" + cr.right + " x " + cr.bottom + "]");
        }

        // ── 부모 / 소유자 / 루트 ──
        HWND parent = User32.INSTANCE.GetParent(hwnd);
        if (parent != null && Pointer.nativeValue(parent.getPointer()) != 0L) {
            kv.put("Parent", hwndDesc(parent));
        }
        HWND owner = User32Ex.INSTANCE.GetWindow(hwnd, GW_OWNER);
        long ownerPeer = (owner == null) ? 0L : Pointer.nativeValue(owner.getPointer());
        long parentPeer = (parent == null) ? 0L : Pointer.nativeValue(parent.getPointer());
        if (ownerPeer != 0L && ownerPeer != parentPeer) {
            kv.put("Owner", hwndDesc(owner));
        }
        HWND root = User32Ex.INSTANCE.GetAncestor(hwnd, GA_ROOT);
        long rootPeer = (root == null) ? 0L : Pointer.nativeValue(root.getPointer());
        if (rootPeer != 0L && rootPeer != peer) {
            kv.put("Root", hwndDesc(root));
        }
        HWND rootOwner = User32Ex.INSTANCE.GetAncestor(hwnd, GA_ROOTOWNER);
        long rootOwnerPeer = (rootOwner == null) ? 0L : Pointer.nativeValue(rootOwner.getPointer());
        if (rootOwnerPeer != 0L && rootOwnerPeer != peer && rootOwnerPeer != rootPeer) {
            kv.put("RootOwner", hwndDesc(rootOwner));
        }

        // ── ChildAtPoint: 같은 윈도우 안에 자식이 있으면 drill down ──
        try {
            POINT.ByValue clientPt = new POINT.ByValue();
            clientPt.x = cursor.x;
            clientPt.y = cursor.y;
            // ScreenToClient로 client 좌표로 변환
            User32Ex.INSTANCE.ScreenToClient(hwnd, clientPt);
            HWND child = User32Ex.INSTANCE.ChildWindowFromPointEx(hwnd, clientPt,
                    CWP_SKIPINVISIBLE | CWP_SKIPTRANSPARENT);
            if (child != null && Pointer.nativeValue(child.getPointer()) != 0L
                    && Pointer.nativeValue(child.getPointer()) != peer) {
                kv.put("ChildAtPoint", hwndDesc(child));
            }
        } catch (Throwable ignored) { }

        // ── 부모 체인 (root → 자식, 들여쓰기) ──
        StringBuilder chainSb = new StringBuilder();
        java.util.Deque<HWND> chain = new java.util.ArrayDeque<>();
        HWND cur = hwnd;
        chain.push(cur);
        for (int i = 0; i < MAX_PARENT_DEPTH; i++) {
            HWND p = User32.INSTANCE.GetParent(cur);
            if (p == null || Pointer.nativeValue(p.getPointer()) == 0L) break;
            chain.push(p);
            cur = p;
        }
        int depth = 0;
        for (HWND h : chain) {
            for (int i = 0; i < depth; i++) chainSb.append("  ");
            chainSb.append(depth == 0 ? "" : "└ ");
            chainSb.append(hwndDesc(h)).append("\n");
            depth++;
        }

        StringBuilder out = new StringBuilder();
        out.append(formatKv(kv));
        out.append("\n── Parent chain (root → 자식) ──\n");
        out.append(chainSb);

        out.append("\n── 힌트 ──\n");
        out.append("· Class가 WGToSH/WndBroker_GP면 GoldNet IPC 컨테이너 = 통신 대상\n");
        out.append("· Class에 Internet Explorer_Server / Chrome_RenderWidgetHost / WebView2 가\n");
        out.append("  보이면 그 안에 HTML이 있음 → 요소 단위 검사는 tools/FlaUInspect/FlaUInspect.exe\n");
        out.append("· ControlID·ClassName·Style 조합으로 같은 화면 내 요소 구분 가능 (전송 시 키로 활용)\n");

        return out.toString();
    }

    private static String formatKv(Map<String, String> kv) {
        // key 폭 자동 계산 (정렬용)
        int maxKey = 6;
        for (String k : kv.keySet()) maxKey = Math.max(maxKey, k.length());
        String fmt = "%-" + maxKey + "s : %s%n";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : kv.entrySet()) {
            sb.append(String.format(fmt, e.getKey(), e.getValue()));
        }
        return sb.toString();
    }

    /** 한 윈도우의 핵심 정보 한 줄 요약 (parent chain / owner 표시용) */
    private String hwndDesc(HWND h) {
        long p = Pointer.nativeValue(h.getPointer());
        String c = getClassName(h);
        String t = getWindowText(h);
        StringBuilder sb = new StringBuilder();
        sb.append("0x").append(Long.toHexString(p).toUpperCase()).append("  [").append(c).append("]");
        if (!t.isEmpty()) sb.append("  \"").append(truncate(t, 40)).append("\"");
        return sb.toString();
    }

    // ────────── 스타일 디코딩 ──────────

    private static final long WS_OVERLAPPED = 0x00000000L;
    private static final long WS_POPUP = 0x80000000L;
    private static final long WS_CHILD = 0x40000000L;
    private static final long WS_MINIMIZE = 0x20000000L;
    private static final long WS_VISIBLE = 0x10000000L;
    private static final long WS_DISABLED = 0x08000000L;
    private static final long WS_CLIPSIBLINGS = 0x04000000L;
    private static final long WS_CLIPCHILDREN = 0x02000000L;
    private static final long WS_MAXIMIZE = 0x01000000L;
    private static final long WS_CAPTION = 0x00C00000L;
    private static final long WS_BORDER = 0x00800000L;
    private static final long WS_DLGFRAME = 0x00400000L;
    private static final long WS_VSCROLL = 0x00200000L;
    private static final long WS_HSCROLL = 0x00100000L;
    private static final long WS_SYSMENU = 0x00080000L;
    private static final long WS_THICKFRAME = 0x00040000L;
    private static final long WS_GROUP = 0x00020000L;
    private static final long WS_TABSTOP = 0x00010000L;

    private static final long WS_EX_DLGMODALFRAME = 0x00000001L;
    private static final long WS_EX_NOPARENTNOTIFY = 0x00000004L;
    private static final long WS_EX_TOPMOST = 0x00000008L;
    private static final long WS_EX_ACCEPTFILES = 0x00000010L;
    private static final long WS_EX_TRANSPARENT = 0x00000020L;
    private static final long WS_EX_MDICHILD = 0x00000040L;
    private static final long WS_EX_TOOLWINDOW = 0x00000080L;
    private static final long WS_EX_WINDOWEDGE = 0x00000100L;
    private static final long WS_EX_CLIENTEDGE = 0x00000200L;
    private static final long WS_EX_CONTEXTHELP = 0x00000400L;
    private static final long WS_EX_RIGHT = 0x00001000L;
    private static final long WS_EX_RTLREADING = 0x00002000L;
    private static final long WS_EX_LEFTSCROLLBAR = 0x00004000L;
    private static final long WS_EX_CONTROLPARENT = 0x00010000L;
    private static final long WS_EX_STATICEDGE = 0x00020000L;
    private static final long WS_EX_APPWINDOW = 0x00040000L;
    private static final long WS_EX_LAYERED = 0x00080000L;
    private static final long WS_EX_NOINHERITLAYOUT = 0x00100000L;
    private static final long WS_EX_LAYOUTRTL = 0x00400000L;
    private static final long WS_EX_COMPOSITED = 0x02000000L;
    private static final long WS_EX_NOACTIVATE = 0x08000000L;

    private static String decodeStyle(long s) {
        List<String> flags = new ArrayList<>();
        if ((s & WS_POPUP) == WS_POPUP) flags.add("WS_POPUP");
        if ((s & WS_CHILD) == WS_CHILD) flags.add("WS_CHILD");
        if ((s & WS_MINIMIZE) != 0) flags.add("WS_MINIMIZE");
        if ((s & WS_VISIBLE) != 0) flags.add("WS_VISIBLE");
        if ((s & WS_DISABLED) != 0) flags.add("WS_DISABLED");
        if ((s & WS_CLIPSIBLINGS) != 0) flags.add("WS_CLIPSIBLINGS");
        if ((s & WS_CLIPCHILDREN) != 0) flags.add("WS_CLIPCHILDREN");
        if ((s & WS_MAXIMIZE) != 0) flags.add("WS_MAXIMIZE");
        if ((s & WS_CAPTION) == WS_CAPTION) flags.add("WS_CAPTION");
        if ((s & WS_BORDER) != 0 && (s & WS_CAPTION) != WS_CAPTION) flags.add("WS_BORDER");
        if ((s & WS_DLGFRAME) != 0 && (s & WS_CAPTION) != WS_CAPTION) flags.add("WS_DLGFRAME");
        if ((s & WS_VSCROLL) != 0) flags.add("WS_VSCROLL");
        if ((s & WS_HSCROLL) != 0) flags.add("WS_HSCROLL");
        if ((s & WS_SYSMENU) != 0) flags.add("WS_SYSMENU");
        if ((s & WS_THICKFRAME) != 0) flags.add("WS_THICKFRAME");
        if ((s & WS_GROUP) != 0) flags.add("WS_GROUP");
        if ((s & WS_TABSTOP) != 0) flags.add("WS_TABSTOP");
        return flags.isEmpty() ? "(없음)" : String.join("|", flags);
    }

    private static String decodeExStyle(long s) {
        List<String> flags = new ArrayList<>();
        if ((s & WS_EX_DLGMODALFRAME) != 0) flags.add("DLGMODALFRAME");
        if ((s & WS_EX_NOPARENTNOTIFY) != 0) flags.add("NOPARENTNOTIFY");
        if ((s & WS_EX_TOPMOST) != 0) flags.add("TOPMOST");
        if ((s & WS_EX_ACCEPTFILES) != 0) flags.add("ACCEPTFILES");
        if ((s & WS_EX_TRANSPARENT) != 0) flags.add("TRANSPARENT");
        if ((s & WS_EX_MDICHILD) != 0) flags.add("MDICHILD");
        if ((s & WS_EX_TOOLWINDOW) != 0) flags.add("TOOLWINDOW");
        if ((s & WS_EX_WINDOWEDGE) != 0) flags.add("WINDOWEDGE");
        if ((s & WS_EX_CLIENTEDGE) != 0) flags.add("CLIENTEDGE");
        if ((s & WS_EX_CONTEXTHELP) != 0) flags.add("CONTEXTHELP");
        if ((s & WS_EX_RIGHT) != 0) flags.add("RIGHT");
        if ((s & WS_EX_RTLREADING) != 0) flags.add("RTLREADING");
        if ((s & WS_EX_LEFTSCROLLBAR) != 0) flags.add("LEFTSCROLLBAR");
        if ((s & WS_EX_CONTROLPARENT) != 0) flags.add("CONTROLPARENT");
        if ((s & WS_EX_STATICEDGE) != 0) flags.add("STATICEDGE");
        if ((s & WS_EX_APPWINDOW) != 0) flags.add("APPWINDOW");
        if ((s & WS_EX_LAYERED) != 0) flags.add("LAYERED");
        if ((s & WS_EX_NOINHERITLAYOUT) != 0) flags.add("NOINHERITLAYOUT");
        if ((s & WS_EX_LAYOUTRTL) != 0) flags.add("LAYOUTRTL");
        if ((s & WS_EX_COMPOSITED) != 0) flags.add("COMPOSITED");
        if ((s & WS_EX_NOACTIVATE) != 0) flags.add("NOACTIVATE");
        return flags.isEmpty() ? "(없음)" : "WS_EX_" + String.join("|WS_EX_", flags);
    }

    // ────────── Win32 헬퍼 ──────────

    private static String getClassName(HWND hwnd) {
        char[] buf = new char[256];
        int n = User32.INSTANCE.GetClassName(hwnd, buf, buf.length);
        return n > 0 ? new String(buf, 0, n) : "";
    }

    private static String getWindowText(HWND hwnd) {
        char[] buf = new char[512];
        int n = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
        return n > 0 ? new String(buf, 0, n) : "";
    }

    /** WM_GETTEXT — 일부 컨트롤(edit 등)에서 GetWindowText와 다른 값 반환 */
    private static String getTextViaMessage(HWND hwnd, int knownLen) {
        try {
            int bufLen = Math.min(knownLen + 1, 4096);
            char[] buf = new char[bufLen];
            // SendMessage with WM_GETTEXT requires lParam to point to a buffer
            // For cross-process this is auto-marshaled by Windows for known messages
            com.sun.jna.Memory mem = new com.sun.jna.Memory((long) bufLen * 2L);
            mem.clear();
            User32.INSTANCE.SendMessage(hwnd, WM_GETTEXT,
                    new WPARAM(bufLen),
                    new LPARAM(Pointer.nativeValue(mem)));
            String s = mem.getWideString(0);
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    private static int sendIntMessage(HWND hwnd, int msg, int w, int l) {
        try {
            return User32.INSTANCE.SendMessage(hwnd, msg, new WPARAM(w), new LPARAM(l)).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * GetWindowLongPtr는 JNA 버전에 따라 primitive long 또는 LONG_PTR 객체를 돌려준다.
     * reflection으로 양쪽 다 처리.
     */
    private static long readLongPtr(HWND hwnd, int index) {
        try {
            java.lang.reflect.Method m = User32.class.getMethod("GetWindowLongPtr", HWND.class, int.class);
            Object r = m.invoke(User32.INSTANCE, hwnd, index);
            if (r == null) return 0L;
            if (r instanceof Number) return ((Number) r).longValue();
            // LONG_PTR 등 wrapper 객체
            try {
                java.lang.reflect.Method lv = r.getClass().getMethod("longValue");
                Object n = lv.invoke(r);
                return n == null ? 0L : ((Number) n).longValue();
            } catch (Throwable ignore) { return 0L; }
        } catch (Throwable t) {
            // Fallback: 32-bit GetWindowLong
            try {
                java.lang.reflect.Method m = User32.class.getMethod("GetWindowLong", HWND.class, int.class);
                Object r = m.invoke(User32.INSTANCE, hwnd, index);
                return r instanceof Number ? ((Number) r).longValue() : 0L;
            } catch (Throwable t2) { return 0L; }
        }
    }

    private static String getProcessPath(int pid) {
        if (pid == 0) return "(unknown)";
        HANDLE h = null;
        try {
            h = Kernel32.INSTANCE.OpenProcess(
                    WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
            if (h == null) return "(권한 없음 또는 접근 불가)";
            char[] path = new char[1024];
            IntByReference sz = new IntByReference(path.length);
            boolean ok = Kernel32Ex.INSTANCE.QueryFullProcessImageNameW(h, 0, path, sz);
            if (ok && sz.getValue() > 0) {
                return new String(path, 0, sz.getValue());
            }
            return "(QueryFullProcessImageName 실패)";
        } catch (Throwable t) {
            return "(예외: " + t.getClass().getSimpleName() + ")";
        } finally {
            if (h != null) Kernel32.INSTANCE.CloseHandle(h);
        }
    }

    public interface Kernel32Ex extends Kernel32 {
        Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean QueryFullProcessImageNameW(HANDLE hProcess, int dwFlags, char[] lpExeName, IntByReference lpdwSize);
    }

    /** JNA 기본 User32에 없거나 시그니처가 다른 함수 직접 바인딩 */
    public interface User32Ex extends User32 {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);
        HWND WindowFromPoint(POINT.ByValue p);
        HWND ChildWindowFromPointEx(HWND hwndParent, POINT.ByValue pt, int uFlags);
        HWND GetAncestor(HWND hwnd, int gaFlags);
        HWND GetWindow(HWND hwnd, int uCmd);
        int GetDlgCtrlID(HWND hwnd);
        boolean IsWindowEnabled(HWND hwnd);
        boolean IsIconic(HWND hwnd);
        boolean IsZoomed(HWND hwnd);
        boolean IsWindowUnicode(HWND hwnd);
        boolean GetClientRect(HWND hwnd, RECT lpRect);
        boolean ScreenToClient(HWND hwnd, POINT.ByValue point);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String zeroPad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.insert(0, '0');
        return sb.toString();
    }

    private static Font uiFont(float size, int style) {
        return pickFirst(UI_FONT_CHAIN, style, size);
    }

    private static Font monoFont(float size) {
        return pickFirst(MONO_FONT_CHAIN, Font.PLAIN, size);
    }

    private static Font pickFirst(String[] candidates, int style, float size) {
        for (String name : candidates) {
            Font f = new Font(name, style, Math.round(size));
            if (!"Dialog".equals(f.getFamily())) {
                return f.deriveFont(size);
            }
        }
        return new Font(Font.SANS_SERIF, style, Math.round(size)).deriveFont(size);
    }
}
