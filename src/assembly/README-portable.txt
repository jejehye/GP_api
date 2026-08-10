GP API Agent — Portable 배포본
================================

이 폴더는 Windows 어느 PC에서든 별도 설치 없이 실행 가능합니다.
JRE가 동봉되어 있어 시스템에 Java가 설치되지 않아도 동작합니다.

폴더 구성
---------
  GpApi.exe              본 API 서버 (Spring Boot + Swing GUI)
  MockGp.exe             테스트용 가짜 GP
  jre/                   동봉 JRE (Temurin 17 기반 minimal runtime)
  gp-api-1.0.0.jar       fat jar (java -jar 로 직접 실행할 때 사용)
  tools/FlaUInspect/     UI Automation 인스펙터 (FlaUInspect, MIT 라이선스)
                         GpApi 안의 Inspector로는 보이지 않는 HTML 요소까지 검사 가능
  README.txt             이 파일

실행 방법
---------
1) 사외/테스트망 (MockGp 사용)
   - 압축 해제 후 폴더 자체를 그대로 두고 (jre 폴더가 EXE 옆에 있어야 함)
   - MockGp.exe 더블클릭 → "Mock GP" 창 표시
   - GpApi.exe 더블클릭 → "GP API Agent" 창 표시 + Tomcat 8080 기동
   - Mock 우상단 배지가 "● 연결됨 (HTTP)" 으로 바뀌면 IPC 정상
   - 다른 PC/도구에서 다음 호출:
       curl -X POST http://localhost:8080/send/v1/account ^
            -H "Content-Type: application/json" ^
            -d "{\"pw\":\"1234\",\"account\":\"우리은행1002458969333\"}"
   - Mock의 "최근 수신 결과" 카드에 결과 표시

2) 사내망 (실제 GP 사용)
   - 실제 GP 프로그램(WndBroker_GP)이 먼저 실행되어 있어야 함
   - GpApi.exe 더블클릭 → 자동으로 GP 창 검색 후 HWND 등록
   - 위 curl로 호출하면 WM_COPYDATA로 GP에 계좌 JSON 전달

테스트로 보내기 버튼 (Mock)
---------------------------
- "계좌 정보 요청 보내기 (dwData=101)" 버튼
- HTTP IPC가 활성화되면 자동으로 활성화됨
- 클릭 시 GpApi의 마지막 계좌를 Mock에 다시 전송

Window Inspector (GpApi 우하단 버튼)
-------------------------------------
- "Window Inspector 열기" 클릭 → 작은 항상-위 창 표시
- 마우스를 아무 윈도우에나 올리면 200ms마다 정보 갱신:
    HWND, Class, Title, PID, 실행 파일 경로, Rect, Parent chain
- Spacebar 또는 F12: 일시정지/재개 (드릴다운 검사용)
- "클립보드 복사" 버튼: 현재 정보를 텍스트로 복사
- 한계: WebSquare 같은 native 컨테이너 안의 HTML 요소는 같은 HWND 하나로만 잡힘
       → HTML 요소 단위 검사는 tools/FlaUInspect/FlaUInspect.exe 사용

FlaUInspect (HTML/UI 요소 검사)
-------------------------------
tools/FlaUInspect/FlaUInspect.exe 실행
- 메뉴: Mode → Hover Mode 체크
- 마우스를 검사할 요소 위로 옮기면 좌측 트리에 자동으로 elt 표시,
  우측에 AutomationId / Name / ControlType / BoundingRectangle 등 표시
- HTML input의 id (예: ..._TAccount_no_sct_Password) 그대로 노출됨
- WindBroker_GP 같은 web-host 안의 form 요소를 검사할 때 사용

주의사항
--------
- jre 폴더를 EXE에서 분리하면 실행되지 않습니다. 폴더 통째로 이동하세요.
- 8080 포트가 사용 중이면 GpApi.exe 실행 실패 — 충돌하는 프로세스를 종료하세요.
- 8090~8119 중 한 포트를 MockGp가 사용 (자동 선택).
- 두 EXE 모두 같은 권한(둘 다 일반 사용자 또는 둘 다 관리자)으로 실행 권장.

API 명세
--------
POST /send/v1/account
  Body: { "account": "우리은행1002458969333", "pw": "1234" }
  성공: { "result": "success", "wmCopyData": true|false, "httpForwarded": N }
  실패: { "result": "fail", "message": "..." }

문의 및 트러블슈팅 시엔 GpApi/MockGp 두 창의 단계 로그를 함께 확인하세요.
