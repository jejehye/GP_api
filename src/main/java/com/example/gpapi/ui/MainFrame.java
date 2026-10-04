package com.example.gpapi.ui;

import com.example.gpapi.dto.RequestLog;
import com.example.gpapi.event.LogEventBus;
import com.formdev.flatlaf.FlatLightLaf;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RenderingHints;

@Component
public class MainFrame {

    // 디자인 토큰 — 전체 블루 계열
    private static final Color BG          = new Color(0xEEF3FB); // blue-50 tint
    private static final Color SURFACE     = Color.WHITE;
    private static final Color TEXT        = new Color(0x0F172A); // slate-900
    private static final Color MUTED       = new Color(0x64748B); // slate-500
    private static final Color BORDER      = new Color(0xDBE5F2); // blue-100

    private final LogEventBus eventBus;
    private final com.example.gpapi.startup.StartupManager startupManager =
            new com.example.gpapi.startup.StartupManager();

    private JFrame frame;
    private JLabel currentAccountLabel;
    private JCheckBox startupCheck;

    public MainFrame(LogEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void init() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[MainFrame] Headless 환경 — GUI 비활성화");
            return;
        }

        // UI를 동기 빌드 — 이 메서드가 리턴될 때 현재 계좌 표시 영역까지 준비됨.
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                buildUi();
            } else {
                SwingUtilities.invokeAndWait(this::buildUi);
            }
        } catch (Exception e) {
            System.err.println("[MainFrame] UI 빌드 실패: " + e.getMessage());
        }

        // UI가 완전히 빌드된 후에야 listener 등록 → 이 시점부터 발행되는 모든 이벤트가 표시됨.
        // (GpAgentService는 ApplicationReadyEvent에서 시작하므로 여기 등록 시점보다 늦게 발행함)
        eventBus.onRequest(this::appendRequest);
    }

    private void buildUi() {
        try {
            FlatLightLaf.setup();
            // 한글 표시를 위해 전역 기본 폰트를 강제 (FlatLaf 내부 컴포넌트도 적용)
            Font defaultFont = uiFont(13f, Font.PLAIN);
            UIManager.put("defaultFont", new javax.swing.plaf.FontUIResource(defaultFont));
            UIManager.put("Component.focusWidth", 0);
            UIManager.put("Component.innerFocusWidth", 0);
        } catch (Exception ignore) {
        }

        Font mono = monoFont(15f).deriveFont(Font.BOLD);

        frame = new JFrame("[S] 신한투자증권");
        frame.setIconImage(createBadgeIcon("S", new Color(0x2563EB))); // 파랑 = Server/Send (CSendToGPWnd)
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(200, 150);
        frame.setMinimumSize(new java.awt.Dimension(200, 150));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        root.add(buildCenter(mono), BorderLayout.CENTER);
        root.add(buildStartupToggle(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);
    }

    private JComponent buildCenter(Font mono) {
        JPanel accountPanel = new JPanel(new BorderLayout());
        accountPanel.setOpaque(false);
        accountPanel.setBorder(new EmptyBorder(10, 8, 10, 8));

        currentAccountLabel = new JLabel("-", SwingConstants.CENTER);
        currentAccountLabel.setForeground(TEXT);
        currentAccountLabel.setFont(mono);
        accountPanel.add(currentAccountLabel, BorderLayout.CENTER);

        return card("현재 계좌", accountPanel);
    }

    /** 작은 창 하단에 유지되는 Windows 자동 실행 체크박스. */
    private JComponent buildStartupToggle() {
        startupCheck = new JCheckBox("Windows 시작 시 자동 실행");
        startupCheck.setOpaque(false);
        startupCheck.setFocusPainted(false);
        startupCheck.setFont(uiFont(10.5f, Font.PLAIN));

        if (startupManager.isSupported()) {
            startupCheck.setForeground(TEXT);
            startupCheck.setSelected(startupManager.isEnabled());
            startupCheck.setToolTipText("로그온 시 자동 실행: " + startupManager.getExecutablePath());
            startupCheck.addActionListener(e -> applyStartupSetting());
        } else {
            startupCheck.setForeground(MUTED);
            startupCheck.setSelected(false);
            startupCheck.setEnabled(false);
            startupCheck.setToolTipText("GpApi.exe 로 실행할 때만 설정할 수 있습니다");
        }

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(4, 0, 0, 0));
        row.add(startupCheck, BorderLayout.WEST);
        return row;
    }

    private void applyStartupSetting() {
        boolean want = startupCheck.isSelected();
        if (startupManager.setEnabled(want)) return;

        startupCheck.setSelected(!want);
        JOptionPane.showMessageDialog(frame,
                "자동 실행 " + (want ? "등록" : "해제") + "에 실패했습니다.",
                "자동 실행 설정",
                JOptionPane.WARNING_MESSAGE);
    }

    private JComponent card(String title, JComponent body) {
        return card(title, body, null);
    }

    private JComponent card(String title, JComponent body, JComponent headerRight) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(SURFACE);
        card.setBorder(new RoundedBorder(BORDER, 12));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(uiFont(13f, Font.BOLD));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                new EmptyBorder(8, 10, 8, 10)));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        if (headerRight != null) {
            headerPanel.add(headerRight, BorderLayout.EAST);
        }
        card.add(headerPanel, BorderLayout.NORTH);

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.setBorder(new EmptyBorder(2, 4, 6, 4));
        bodyWrap.add(body, BorderLayout.CENTER);
        card.add(bodyWrap, BorderLayout.CENTER);

        // 카드 사이 간격
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setBorder(new EmptyBorder(0, 0, 0, 0));
        outer.add(card, BorderLayout.CENTER);
        return outer;
    }

    private void appendRequest(RequestLog log) {
        SwingUtilities.invokeLater(() -> {
            if (currentAccountLabel != null) {
                currentAccountLabel.setText(log.maskedAccount());
            }
        });
    }

    // ────────── 폰트 헬퍼 ──────────
    // 한글+영문 모두 안정적으로 표시되는 폰트를 우선
    private static final String[] UI_FONT_CHAIN = {
            "Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo",
            "Noto Sans CJK KR", "Noto Sans KR",
            "Segoe UI"
    };
    // 한글 글리프가 없는 Consolas/Cascadia를 빼고, 한글 mono → Malgun Gothic 순으로
    // (Malgun Gothic은 엄밀한 mono는 아니지만 한글이 깨지지 않게 하는 보험)
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
            // 요청한 폰트가 없으면 자바가 family를 "Dialog"로 바꿔버림 — 그건 건너뜀
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

    // ────────── 커스텀 컴포넌트 ──────────

    /** 라운드된 1px 테두리 */
    private static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        RoundedBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        @Override public Insets getBorderInsets(java.awt.Component c) { return new Insets(1, 1, 1, 1); }
        @Override public Insets getBorderInsets(java.awt.Component c, Insets insets) {
            insets.set(1, 1, 1, 1); return insets;
        }
        @Override public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }
    }

}
