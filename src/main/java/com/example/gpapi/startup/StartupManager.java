package com.example.gpapi.startup;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 윈도우 로그온 시 자동 실행 등록/해제.
 *
 * HKCU\Software\Microsoft\Windows\CurrentVersion\Run 에 값을 넣고 빼는 방식이라
 * 관리자 권한이 필요 없고, 현재 사용자에게만 적용된다.
 * 레지스트리 조작은 shell 없이 reg.exe 를 인자 배열로 직접 실행한다.
 */
public class StartupManager {

    private static final String RUN_KEY    = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "GpApi";

    /** launch4j가 -Dgpapi.exe="%EXEFILE%" 로 넘겨주는 EXE 절대경로 */
    private static final String EXE_PROPERTY = "gpapi.exe";

    private static final long CMD_TIMEOUT_SEC = 10;

    private final String exePath; // null 이면 자동 실행 기능 사용 불가

    public StartupManager() {
        this.exePath = resolveExecutable();
    }

    /**
     * 자동 실행 기능을 쓸 수 있는 환경인지.
     * 윈도우가 아니거나 EXE가 아닌 방식(IDE/jar 직접 실행)으로 떠 있으면 false.
     */
    public boolean isSupported() {
        return isWindows() && exePath != null;
    }

    /** 등록해 둔 EXE 경로 (미지원 환경이면 null) */
    public String getExecutablePath() {
        return exePath;
    }

    /** 현재 자동 실행이 등록돼 있는지 */
    public boolean isEnabled() {
        if (!isSupported()) return false;
        try {
            return run("reg", "query", RUN_KEY, "/v", VALUE_NAME) == 0;
        } catch (Exception e) {
            System.err.println("[StartupManager] 등록 상태 조회 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * 자동 실행 등록/해제.
     *
     * @return 요청한 상태로 반영됐으면 true
     */
    public boolean setEnabled(boolean enabled) {
        if (!isSupported()) return false;
        try {
            int exit;
            if (enabled) {
                // 레지스트리에 따옴표까지 포함해 저장한다: "C:\...\GpApi.exe"
                // 따옴표가 없으면 경로에 공백이 있을 때 윈도우가 앞 토큰부터 실행을 시도해
                // 엉뚱한 실행 파일이 대신 실행될 수 있다 (unquoted path 문제).
                // reg.exe 인자 파서가 따옴표를 벗겨내므로 \" 로 이스케이프해서 넘긴다.
                String data = "\\\"" + exePath + "\\\"";
                exit = run("reg", "add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", data, "/f");
            } else {
                exit = run("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f");
            }
            return exit == 0;
        } catch (Exception e) {
            System.err.println("[StartupManager] 등록 변경 실패: " + e.getMessage());
            return false;
        }
    }

    /** reg.exe 실행 후 종료 코드 반환. 출력은 버린다. */
    private int run(String... command) throws Exception {
        Process p = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!p.waitFor(CMD_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("reg 명령이 응답하지 않아 중단함");
        }
        return p.exitValue();
    }

    /**
     * 등록에 사용할 EXE 경로 결정.
     *
     * 1) launch4j가 넘겨준 시스템 속성 (정상 EXE 실행 경로)
     * 2) 실패 시 현재 프로세스(javaw.exe)가 번들 JRE 안에 있으면 EXE 위치를 역산
     *    (EXE 옆 jre/bin/javaw.exe 구조를 이용)
     */
    private static String resolveExecutable() {
        if (!isWindows()) return null;

        String fromProp = System.getProperty(EXE_PROPERTY);
        if (fromProp != null && !fromProp.isBlank()) {
            File f = new File(fromProp.trim());
            if (f.isFile()) return f.getAbsolutePath();
        }

        // 번들 JRE로 실행된 경우: <EXE 폴더>/jre/bin/javaw.exe → <EXE 폴더>/GpApi.exe
        try {
            String cmd = ProcessHandle.current().info().command().orElse(null);
            if (cmd != null) {
                Path bin = Paths.get(cmd).getParent();               // .../jre/bin
                Path jre = (bin == null) ? null : bin.getParent();    // .../jre
                Path dir = (jre == null) ? null : jre.getParent();    // EXE 폴더
                if (dir != null) {
                    File exe = dir.resolve("GpApi.exe").toFile();
                    if (exe.isFile()) return exe.getAbsolutePath();
                }
            }
        } catch (Exception ignore) {
            // 경로 역산 실패 — 미지원으로 처리
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
