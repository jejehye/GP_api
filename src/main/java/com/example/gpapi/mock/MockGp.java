package com.example.gpapi.mock;

import com.formdev.flatlaf.FlatLightLaf;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.net.httpserver.HttpServer;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.WinDef.ATOM;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실제 GP 프로그램(WndBroker_GP)을 흉내 내는 테스트용 프로그램.
 * 클래스명="WGToSH", 창 제목="WndBroker_GP" 로 등록되어 GpApi의 FindWindow에 잡힌다.
 *
 * 동작:
 *   1) GpApi가 dwData=100 (HWND 등록)을 보내면 자동으로 dwData=101 (계좌정보 요청)을 응답한다.
 *   2) GpApi가 dwData=102 (계좌 JSON)을 보내면 파싱하여 표시한다.
 *
 * 주의: Mock은 GpApi를 능동적으로 찾지 않는다.
 *      GpApi가 Mock을 발견 → 100 송신 → Mock이 101 응답 → GpApi가 102 송신.
 */
public class MockGp {

    private static final int WM_COPYDATA = 0x004A;
    private static final int MOCK_HTTP_PORT = 8090;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // 디자인 토큰 — 블루
    private static final Color BG         = new Color(0xEEF3FB);
    private static final Color SURFACE    = Color.WHITE;
    private static final Color TEXT       = new Color(0x0F172A);
    private static final Color MUTED      = new Color(0x64748B);
    private static final Color BORDER     = new Color(0xDBE5F2);
    private static final Color ACCENT     = new Color(0x2563EB);
    private static final Color ACCENT_FG  = Color.WHITE;
    private static final Color OK_FG      = new Color(0x117A3D);

    private static HWND mockHwnd;
    /** GpApi 쪽 hidden window HWND — dwData=100 수신 시 등록됨 */
    private static volatile HWND gpApiHwnd;

    // WindowProc 콜백 강참조 (GC 방지)
    private static WindowProc windowProcRef;

    private static JTextArea logArea;
    private static JButton replayBtn;
    private static StatusBadge connBadge;
    private static JLabel lastReceivedLabel;
    private static int receiveCount = 0;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MockGp::buildUi);
        createMockWindow();
        startStatusHttpServer();

        MSG msg = new MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
    }

    /** 8090 포트에 단순 상태 엔드포인트만 노출 (데이터 흐름은 WM_COPYDATA). */
    private static void startStatusHttpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", MOCK_HTTP_PORT), 0);
            server.createContext("/status", exchange -> {
                String resp = "{\"ok\":true,\"connected\":" + (gpApiHwnd != null)
                        + ",\"received\":" + receiveCount + "}";
                byte[] body = resp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.setExecutor(null);
            server.start();
            log("Mock HTTP 상태 서버 시작: http://localhost:" + MOCK_HTTP_PORT + "/status");
        } catch (Exception e) {
            log("⚠ Mock HTTP 상태 서버 시작 실패: " + e.getMessage());
        }
    }

    private static void buildUi() {
        try {
            FlatLightLaf.setup();
            Font defaultFont = uiFont(13f, Font.PLAIN);
            UIManager.put("defaultFont", new javax.swing.plaf.FontUIResource(defaultFont));
            UIManager.put("Component.focusWidth", 0);
            UIManager.put("Component.innerFocusWidth", 0);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.trackArc", 999);
            UIManager.put("ScrollBar.width", 10);
        } catch (Exception ignore) {
        }

        JFrame frame = new JFrame("[M] Mock GP — WndBroker_GP");
        frame.setIconImage(createBadgeIcon("M", new Color(0xDC2626))); // 빨강 = Mock
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(880, 600);
        frame.setMinimumSize(new Dimension(720, 480));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);

        log("Mock GP 시작 — class=WGToSH / title=WndBroker_GP");
        log("GpApi가 우리를 발견하고 dwData=100을 보내기를 대기 중...");
    }

    private static JComponent buildHeader() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(true);
        bar.setBackground(SURFACE);
        bar.setBorder(new CompoundBorder(
                new RoundedBorder(BORDER, 12),
                new EmptyBorder(14, 18, 14, 18)));

        JPanel titleWrap = new JPanel();
        titleWrap.setOpaque(false);
        titleWrap.setLayout(new javax.swing.BoxLayout(titleWrap, javax.swing.BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Mock GP");
        title.setForeground(TEXT);
        title.setFont(uiFont(17f, Font.BOLD));
        JLabel subtitle = new JLabel("WGToSH / WndBroker_GP — 테스트용 가짜 GP");
        subtitle.setForeground(MUTED);
        subtitle.setFont(uiFont(12f, Font.PLAIN));
        subtitle.setBorder(new EmptyBorder(2, 0, 0, 0));
        titleWrap.add(title);
        titleWrap.add(subtitle);

        connBadge = new StatusBadge("연결 대기", false);

        bar.add(titleWrap, BorderLayout.WEST);
        bar.add(connBadge, BorderLayout.EAST);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(bar, BorderLayout.CENTER);
        wrap.setBorder(new EmptyBorder(0, 0, 14, 0));
        return wrap;
    }

    private static JComponent buildCenter() {
        JComponent summary = card("최근 수신 결과", buildSummary());
        JComponent logCard = card("이벤트 로그", buildLogArea());

        JPanel grid = new JPanel(new BorderLayout(0, 14));
        grid.setOpaque(false);

        JPanel summaryWrap = new JPanel(new BorderLayout());
        summaryWrap.setOpaque(false);
        summaryWrap.add(summary, BorderLayout.CENTER);
        summaryWrap.setPreferredSize(new Dimension(0, 130));

        grid.add(summaryWrap, BorderLayout.NORTH);
        grid.add(logCard, BorderLayout.CENTER);
        return grid;
    }

    private static JComponent buildSummary() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 18, 14, 18));

        lastReceivedLabel = new JLabel(html("<span style='color:#64748B;'>아직 수신된 데이터가 없습니다.</span>"));
        lastReceivedLabel.setFont(uiFont(13f, Font.PLAIN));
        lastReceivedLabel.setVerticalAlignment(JLabel.TOP);
        p.add(lastReceivedLabel, BorderLayout.CENTER);
        return p;
    }

    private static JComponent buildLogArea() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(monoFont(12.5f));
        logArea.setForeground(TEXT);
        logArea.setBackground(SURFACE);
        logArea.setBorder(new EmptyBorder(8, 14, 8, 14));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(SURFACE);
        scroll.setOpaque(false);
        return scroll;
    }

    private static JComponent buildFooter() {
        replayBtn = new JButton("계좌정보 재요청 (dwData=101)");
        replayBtn.setEnabled(false);
        replayBtn.setFocusPainted(false);
        replayBtn.setFont(uiFont(12.5f, Font.BOLD));
        replayBtn.setBackground(ACCENT);
        replayBtn.setForeground(ACCENT_FG);
        replayBtn.putClientProperty("JButton.buttonType", "roundRect");
        replayBtn.setBorder(new EmptyBorder(10, 16, 10, 16));
        replayBtn.addActionListener(e -> sendRequest101("수동 재요청"));

        JButton clearBtn = new JButton("로그 지우기");
        clearBtn.setFocusPainted(false);
        clearBtn.setFont(uiFont(12.5f, Font.PLAIN));
        clearBtn.putClientProperty("JButton.buttonType", "roundRect");
        clearBtn.addActionListener(e -> {
            if (logArea != null) logArea.setText("");
        });

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new javax.swing.BoxLayout(right, javax.swing.BoxLayout.X_AXIS));
        right.add(clearBtn);
        right.add(javax.swing.Box.createHorizontalStrut(8));
        right.add(replayBtn);

        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setOpaque(false);
        buttonRow.add(right, BorderLayout.EAST);

        JLabel hint = new JLabel("프로토콜  100=GpApi→Mock(HWND 등록) · 101=Mock→GpApi(요청) · 102=GpApi→Mock(계좌 JSON)");
        hint.setForeground(MUTED);
        hint.setFont(uiFont(11.5f, Font.PLAIN));
        JPanel hintRow = new JPanel(new BorderLayout());
        hintRow.setOpaque(false);
        hintRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        hintRow.add(hint, BorderLayout.WEST);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(14, 0, 0, 0));
        bar.add(buttonRow, BorderLayout.NORTH);
        bar.add(hintRow, BorderLayout.SOUTH);
        return bar;
    }

    private static JComponent card(String title, JComponent body) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(SURFACE);
        card.setBorder(new RoundedBorder(BORDER, 12));

        JLabel header = new JLabel(title);
        header.setForeground(TEXT);
        header.setFont(uiFont(13f, Font.BOLD));
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                new EmptyBorder(12, 16, 12, 16)));
        card.add(header, BorderLayout.NORTH);

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.setBorder(new EmptyBorder(0, 4, 6, 4));
        bodyWrap.add(body, BorderLayout.CENTER);
        card.add(bodyWrap, BorderLayout.CENTER);
        return card;
    }

    private static void createMockWindow() {
        String className = "WGToSH";

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

        ATOM atom = User32.INSTANCE.RegisterClassEx(wc);
        if (atom == null || atom.intValue() == 0) {
            log("RegisterClassEx 실패");
            return;
        }

        mockHwnd = User32.INSTANCE.CreateWindowEx(
                0, className, "WndBroker_GP", 0,
                0, 0, 0, 0, null, null, null, null
        );

        long peer = mockHwnd == null ? 0 : Pointer.nativeValue(mockHwnd.getPointer());
        log("Mock GP 윈도우 생성 완료: " + mockHwnd + " (peer=0x" + Long.toHexString(peer) + ")");

        // UIPI 우회
        try {
            boolean allowed = User32Ex.INSTANCE.ChangeWindowMessageFilterEx(
                    mockHwnd, WM_COPYDATA, MSGFLT_ALLOW, Pointer.NULL);
            log("UIPI 메시지 필터 허용 (WM_COPYDATA): " + (allowed ? "OK" : "실패"));
        } catch (Throwable t) {
            try {
                boolean allowed = User32Ex.INSTANCE.ChangeWindowMessageFilter(WM_COPYDATA, MSGFLT_ADD);
                log("UIPI 메시지 필터(legacy) 허용: " + (allowed ? "OK" : "실패"));
            } catch (Throwable t2) {
                log("UIPI 필터 호출 실패 (무시): " + t2.getMessage());
            }
        }
    }

    private static final int MSGFLT_ALLOW = 1;
    private static final int MSGFLT_ADD   = 1;

    public interface User32Ex extends User32 {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean ChangeWindowMessageFilterEx(HWND hWnd, int message, int action, Pointer pChangeFilterStruct);
        boolean ChangeWindowMessageFilter(int message, int dwFlag);
    }

    private static LRESULT windowProc(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        if (uMsg == WM_COPYDATA) {
            // lParam.toPointer() 가 JNA 5.14에서 opaque pointer를 반환하는 문제 회피
            Pointer raw = new Pointer(lParam.longValue());
            COPYDATASTRUCT cds = new COPYDATASTRUCT(raw);
            cds.read();

            long dwData = cds.dwData.longValue();
            long sender = wParam == null ? 0 : wParam.longValue();
            log("◀ WM_COPYDATA 수신 (dwData=" + dwData + ", sender=0x" + Long.toHexString(sender) + ")");

            if (dwData == 100) {
                // GpApi가 자기 HWND를 등록 — 페이로드에서 HWND 추출
                // GpApi 가 4바이트(32-bit, Win32 GoldNet 호환) 또는 8바이트(64-bit) 둘 다 보낼 수 있어 둘 다 수용
                long agentPeer;
                if (cds.cbData == 4) {
                    agentPeer = ((long) cds.lpData.getInt(0)) & 0xFFFFFFFFL;
                } else if (cds.cbData == 8) {
                    agentPeer = Pointer.nativeValue(cds.lpData.getPointer(0));
                } else {
                    log("  └ [100] 예상치 못한 cbData=" + cds.cbData + " — 4 또는 8 byte 기대");
                    return new LRESULT(0);
                }
                gpApiHwnd = new HWND(new Pointer(agentPeer));
                log("  └ [100] GpApi HWND 등록 완료: 0x" + Long.toHexString(agentPeer)
                        + " (cbData=" + cds.cbData + ")");
                SwingUtilities.invokeLater(() -> {
                    if (replayBtn != null) replayBtn.setEnabled(true);
                    if (connBadge != null) connBadge.set("연결됨", true);
                });
                // GpApi가 발견하고 100을 보냈으니 우리는 101 (계좌정보 요청)으로 응답
                sendRequest101("자동 응답 (100 수신 → 101 송신)");

            } else if (dwData == 102) {
                int len = cds.cbData;
                byte[] data = cds.lpData.getByteArray(0, len);
                String json = new String(data, StandardCharsets.UTF_8).replace("\0", "");
                receiveCount++;

                log("  └ [102] 계좌 JSON 페이로드 (" + len + " bytes):");
                for (String line : json.split("\\r?\\n")) {
                    log("        " + line);
                }

                Map<String, String> parsed = parseFlat(json);
                String acct = parsed.getOrDefault("acct_no", "(없음)");
                // 실 GoldNet 포맷: 비밀번호 키는 'acct_pw' (d 없음)
                String pwRaw = parsed.getOrDefault("acct_pw",
                        parsed.getOrDefault("acct_pwd", "")); // 구버전 호환
                String pwMasked = pwRaw.isEmpty() ? "(빈값)"
                        : "*".repeat(pwRaw.length()) + " (" + pwRaw.length() + "자리)";

                log("  ✓ [102] 처리 결과: 계좌=" + acct + ", 비밀번호=" + pwMasked);
                updateSummary(acct, pwMasked, len, receiveCount);

            } else {
                log("  └ 알 수 없는 dwData=" + dwData);
            }

            return new LRESULT(1);
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    /** dwData=101 (연결 확인 ack "ok")을 등록된 GpApi에 송신. */
    private static void sendRequest101(String reason) {
        if (gpApiHwnd == null) {
            log("⚠ [101] 송신 불가 — GpApi HWND 미등록 (dwData=100을 먼저 수신해야 함)");
            return;
        }
        try {
            // 페이로드 = "ok" 문자열 (NUL 종단 포함). 실 GoldNet 샘플과 동일.
            byte[] payload = ("ok\0").getBytes(StandardCharsets.UTF_8);
            Memory mem = new Memory(payload.length);
            mem.write(0, payload, 0, payload.length);

            COPYDATASTRUCT cds = new COPYDATASTRUCT();
            cds.dwData = new ULONG_PTR(101);
            cds.cbData = payload.length;
            cds.lpData = mem;
            cds.write();

            LRESULT r = User32.INSTANCE.SendMessage(
                    gpApiHwnd, WM_COPYDATA,
                    new WPARAM(Pointer.nativeValue(mockHwnd.getPointer())),
                    new LPARAM(Pointer.nativeValue(cds.getPointer()))
            );
            long lr = r == null ? -1 : r.longValue();
            if (lr == 1) {
                log("▶ [101] 연결 ack \"ok\" 송신 (" + reason + ") → LRESULT=" + lr + " (OK)");
            } else {
                int err = Native.getLastError();
                log("⚠ [101] 송신 실패 → LRESULT=" + lr + " (GetLastError=" + err + ")");
            }
        } catch (Exception e) {
            log("⚠ [101] 송신 예외: " + e.getMessage());
        }
    }

    /** JSON 평면 키-값을 정규식으로 추출 (간단 파서) */
    private static Map<String, String> parseFlat(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        while (m.find()) {
            out.putIfAbsent(m.group(1), m.group(2));
        }
        return out;
    }

    private static void updateSummary(String account, String pwMasked, int bytes, int total) {
        if (lastReceivedLabel == null) return;
        String time = LocalTime.now().format(TIME);
        String html = "<html><div style=\"font-family:'Malgun Gothic', 'Segoe UI', sans-serif;\">"
                + "<div style='color:#117A3D; font-weight:bold;'>● 처리 완료</div>"
                + "<div style='margin-top:4px; color:#0F172A;'>"
                + "<b>계좌</b> &nbsp;" + escape(account) + "<br>"
                + "<b>비밀번호</b> &nbsp;" + escape(pwMasked)
                + "</div>"
                + "<div style='margin-top:4px; color:#64748B; font-size:11px;'>"
                + time + " · " + bytes + " bytes · 누적 " + total + "건"
                + "</div>"
                + "</div></html>";
        SwingUtilities.invokeLater(() -> lastReceivedLabel.setText(html));
    }

    private static void log(String s) {
        String line = "[" + LocalTime.now().format(TIME) + "] " + s;
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(line + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
        System.out.println(line);
    }

    private static String html(String inner) { return "<html>" + inner + "</html>"; }
    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final String[] UI_FONT_CHAIN = {
            "Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo",
            "Noto Sans CJK KR", "Noto Sans KR",
            "Segoe UI"
    };
    private static final String[] MONO_FONT_CHAIN = {
            "D2Coding", "나눔고딕코딩", "NanumGothicCoding",
            "Sarasa Mono K", "Sarasa Mono SC",
            "Malgun Gothic"
    };
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

    /** 둥근 사각형 배경 + 흰색 큰 글자 한 글자 — taskbar 식별용 아이콘 */
    private static java.awt.image.BufferedImage createBadgeIcon(String letter, Color bg) {
        int size = 64;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, size, size, 18, 18);
        g2.setColor(Color.WHITE);
        Font badgeFont = new Font("Segoe UI", Font.BOLD, 46);
        g2.setFont(badgeFont);
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int x = (size - fm.stringWidth(letter)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(letter, x, y);
        g2.dispose();
        return img;
    }

    private static class RoundedBorder extends AbstractBorder {
        private final Color color; private final int radius;
        RoundedBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        @Override public Insets getBorderInsets(Component c, Insets insets) { insets.set(1,1,1,1); return insets; }
        @Override public Insets getBorderInsets(Component c) { return new Insets(1,1,1,1); }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w-1, h-1, radius, radius);
            g2.dispose();
        }
    }

    private static class StatusBadge extends JPanel {
        private final JLabel label = new JLabel();
        private final Dot dot = new Dot(MUTED);
        private boolean active;
        StatusBadge(String text, boolean active) {
            setOpaque(false);
            setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS));
            setBorder(new EmptyBorder(6, 12, 6, 12));
            label.setFont(uiFont(12f, Font.BOLD));
            add(dot);
            add(javax.swing.Box.createHorizontalStrut(8));
            add(label);
            set(text, active);
        }
        void set(String text, boolean active) {
            this.active = active;
            label.setText(text);
            label.setForeground(active ? OK_FG : MUTED);
            dot.setColor(active ? new Color(0x16A34A) : MUTED);
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(active ? new Color(0xE7F8EE) : new Color(0xF1F5F9));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class Dot extends JComponent {
        private Color color;
        Dot(Color color) {
            this.color = color;
            Dimension d = new Dimension(10, 10);
            setPreferredSize(d); setMinimumSize(d); setMaximumSize(d);
        }
        void setColor(Color c) { this.color = c; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.dispose();
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
