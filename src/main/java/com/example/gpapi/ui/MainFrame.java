package com.example.gpapi.ui;

import com.example.gpapi.dto.RequestLog;
import com.example.gpapi.event.LogEventBus;
import com.formdev.flatlaf.FlatLightLaf;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;

@Component
public class MainFrame {

    // 디자인 토큰 — 전체 블루 계열
    private static final Color BG          = new Color(0xEEF3FB); // blue-50 tint
    private static final Color SURFACE     = Color.WHITE;
    private static final Color TEXT        = new Color(0x0F172A); // slate-900
    private static final Color MUTED       = new Color(0x64748B); // slate-500
    private static final Color BORDER      = new Color(0xDBE5F2); // blue-100
    private static final Color ACCENT      = new Color(0x2563EB); // blue-600
    private static final Color ACCENT_SOFT = new Color(0xDBEAFE); // blue-100
    private static final Color SUCCESS_BG  = new Color(0xE7F8EE);
    private static final Color SUCCESS_FG  = new Color(0x117A3D);
    private static final Color FAIL_BG     = new Color(0xFDECEC);
    private static final Color FAIL_FG     = new Color(0xB42318);
    private static final Color ROW_ALT     = new Color(0xF8FAFD);
    private static final Color HEADER_BG   = new Color(0xF1F5FB);

    private final LogEventBus eventBus;
    private final com.example.gpapi.inspector.WindowInspector inspector =
            new com.example.gpapi.inspector.WindowInspector();
    private final com.example.gpapi.startup.StartupManager startupManager =
            new com.example.gpapi.startup.StartupManager();

    private JFrame frame;
    private DefaultTableModel requestTableModel;
    private JTable requestTable;
    private JButton inspectorBtn;
    private JCheckBox startupCheck;

    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    public MainFrame(LogEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void init() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[MainFrame] Headless 환경 — GUI 비활성화");
            return;
        }

        // UI를 동기 빌드 — 이 메서드가 리턴될 때 frame/requestTableModel 모두 준비됨.
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
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.trackArc", 999);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("Table.showHorizontalLines", false);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 0));
            UIManager.put("TableHeader.separatorColor", BORDER);
            UIManager.put("TableHeader.bottomSeparatorColor", BORDER);
        } catch (Exception ignore) {
        }

        Font baseFont = uiFont(13f, Font.PLAIN);
        Font mono = monoFont(13f);

        frame = new JFrame("[S] 신한투자증권");
        frame.setIconImage(createBadgeIcon("S", new Color(0x2563EB))); // 파랑 = Server/Send (CSendToGPWnd)
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 350);
        frame.setMinimumSize(new java.awt.Dimension(300, 350));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        root.add(buildCenter(baseFont, mono), BorderLayout.CENTER);
        root.add(buildFooter(baseFont), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);
    }

    private JComponent buildCenter(Font baseFont, Font mono) {
        return card("최근 API 요청", buildRequestTable(baseFont), buildStatusIndicator());
    }

    /** 초록 점 + "실행 중" — 카드 헤더 우측에 배치 */
    private JComponent buildStatusIndicator() {
        JPanel statusWrap = new JPanel();
        statusWrap.setOpaque(false);
        statusWrap.setLayout(new javax.swing.BoxLayout(statusWrap, javax.swing.BoxLayout.X_AXIS));
        statusWrap.add(new Dot(new Color(0x16A34A)));
        statusWrap.add(javax.swing.Box.createHorizontalStrut(6));
        JLabel runState = new JLabel("실행 중");
        runState.setForeground(SUCCESS_FG);
        runState.setFont(uiFont(12.5f, Font.BOLD));
        statusWrap.add(runState);
        return statusWrap;
    }

    private JComponent buildRequestTable(Font baseFont) {
        requestTableModel = new DefaultTableModel(
                new Object[]{"시간", "계좌", "비밀번호", "결과"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        requestTable = new JTable(requestTableModel) {
            @Override
            public java.awt.Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int col) {
                java.awt.Component c = super.prepareRenderer(renderer, row, col);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? SURFACE : ROW_ALT);
                }
                return c;
            }
        };
        requestTable.setFillsViewportHeight(true);
        requestTable.setRowHeight(30);
        requestTable.setFont(baseFont);
        requestTable.setForeground(TEXT);
        requestTable.setBackground(SURFACE);
        requestTable.setShowGrid(false);
        requestTable.setSelectionBackground(ACCENT_SOFT);
        requestTable.setSelectionForeground(TEXT);
        requestTable.setIntercellSpacing(new java.awt.Dimension(0, 0));

        JTableHeader header = requestTable.getTableHeader();
        header.setBackground(HEADER_BG);
        header.setForeground(MUTED);
        header.setFont(uiFont(12f, Font.BOLD));
        header.setReorderingAllowed(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        header.setPreferredSize(new java.awt.Dimension(header.getPreferredSize().width, 32));

        DefaultTableCellRenderer pad = new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                                  boolean hasFocus, int row, int column) {
                java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((DefaultTableCellRenderer) c).setBorder(new EmptyBorder(0, 14, 0, 14));
                return c;
            }
        };
        DefaultTableCellRenderer mutedRenderer = new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                                  boolean hasFocus, int row, int column) {
                java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((DefaultTableCellRenderer) c).setBorder(new EmptyBorder(0, 14, 0, 14));
                if (!isSelected) c.setForeground(MUTED);
                return c;
            }
        };

        requestTable.getColumnModel().getColumn(0).setCellRenderer(mutedRenderer);   // 시간
        requestTable.getColumnModel().getColumn(1).setCellRenderer(pad);             // 계좌
        requestTable.getColumnModel().getColumn(2).setCellRenderer(pad);             // 비밀번호
        requestTable.getColumnModel().getColumn(3).setCellRenderer(new StatusChipRenderer()); // 결과

        requestTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        requestTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        requestTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        requestTable.getColumnModel().getColumn(3).setPreferredWidth(70);

        JScrollPane scroll = new JScrollPane(requestTable);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(SURFACE);
        scroll.setOpaque(false);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JComponent buildFooter(Font baseFont) {
        JButton clearBtn = new JButton("Clear");
        clearBtn.setFocusPainted(false);
        clearBtn.setFont(uiFont(12.5f, Font.PLAIN));
        clearBtn.putClientProperty("JButton.buttonType", "roundRect");
        clearBtn.addActionListener(e -> {
            if (requestTableModel != null) requestTableModel.setRowCount(0);
        });

        inspectorBtn = new JButton("Window Inspector 열기");
        inspectorBtn.setFocusPainted(false);
        inspectorBtn.setFont(uiFont(12.5f, Font.BOLD));
        inspectorBtn.setBackground(ACCENT);
        inspectorBtn.setForeground(Color.WHITE);
        inspectorBtn.putClientProperty("JButton.buttonType", "roundRect");
        inspectorBtn.addActionListener(e -> {
            if (inspector.isShowing()) {
                inspector.hide();
                inspectorBtn.setText("Window Inspector 열기");
            } else {
                inspector.show();
                inspectorBtn.setText("Window Inspector 닫기");
            }
        });

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new javax.swing.BoxLayout(right, javax.swing.BoxLayout.X_AXIS));
        right.add(clearBtn);
        right.add(javax.swing.Box.createHorizontalStrut(8));
        right.add(inspectorBtn);

        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setOpaque(false);
        buttonRow.add(right, BorderLayout.EAST);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(12, 0, 0, 0));
        bar.add(buildStartupToggle(), BorderLayout.NORTH);
        bar.add(buttonRow, BorderLayout.CENTER);
        return bar;
    }

    /** 윈도우 시작 시 자동 실행 체크박스 */
    private JComponent buildStartupToggle() {
        startupCheck = new JCheckBox("Windows 시작 시 자동 실행");
        startupCheck.setOpaque(false);
        startupCheck.setFocusPainted(false);
        startupCheck.setFont(uiFont(12f, Font.PLAIN));

        if (startupManager.isSupported()) {
            startupCheck.setForeground(TEXT);
            startupCheck.setSelected(startupManager.isEnabled());
            startupCheck.setToolTipText("로그온 시 자동 실행: " + startupManager.getExecutablePath());
            startupCheck.addActionListener(e -> applyStartupSetting());
        } else {
            // EXE가 아닌 방식(개발 중 jar/IDE 실행)이면 등록할 경로가 없어 비활성화
            startupCheck.setForeground(MUTED);
            startupCheck.setSelected(false);
            startupCheck.setEnabled(false);
            startupCheck.setToolTipText("GpApi.exe 로 실행할 때만 설정할 수 있습니다");
        }

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(0, 2, 8, 0));
        row.add(startupCheck, BorderLayout.WEST);
        return row;
    }

    /** 체크 상태를 레지스트리에 반영. 실패하면 체크박스를 원래대로 되돌린다. */
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
                new EmptyBorder(12, 16, 12, 16)));
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
            if (requestTableModel == null) return;
            // 항상 최근 1건만 표시 — 기존 행을 비우고 새 행만 남긴다.
            requestTableModel.setRowCount(0);
            requestTableModel.addRow(new Object[]{
                    log.timestamp().format(timeFmt),
                    log.maskedAccount(),
                    log.maskedPw(),
                    log.success() ? "성공" : "실패"
            });
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

    /** 작은 상태 점 */
    private static class Dot extends JComponent {
        private final Color color;
        Dot(Color color) {
            this.color = color;
            setPreferredSize(new java.awt.Dimension(10, 10));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /** 결과 컬럼 — 성공/실패를 칩으로 렌더링 */
    private static class StatusChipRenderer extends DefaultTableCellRenderer {
        private boolean success;
        StatusChipRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(false);
        }
        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                              boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            success = "성공".equals(value);
            setText((String) value);
            setFont(uiFont(11.5f, Font.BOLD));
            setBorder(new EmptyBorder(0, 14, 0, 14));
            setForeground(success ? SUCCESS_FG : FAIL_FG);
            return this;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1) 셀 전체를 행의 모드 배경색으로 먼저 채워서 다른 셀과 연속되게 보이게 한다.
            //    (StatusChipRenderer 는 setOpaque(false) 이므로 직접 채우지 않으면 흰색이 그대로 비침)
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            // 2) 결과 chip pill (성공=초록 / 실패=빨강) 을 셀 가운데에 덧그림
            int padX = 14, padY = 5;
            int textW = getFontMetrics(getFont()).stringWidth(getText());
            int chipW = Math.min(textW + padX * 2, getWidth() - 8);
            int chipH = Math.min(getFontMetrics(getFont()).getHeight() + padY * 2, getHeight() - 6);
            int x = (getWidth() - chipW) / 2;
            int y = (getHeight() - chipH) / 2;
            g2.setColor(success ? SUCCESS_BG : FAIL_BG);
            g2.fillRoundRect(x, y, chipW, chipH, chipH, chipH);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
