# GP API Agent

Spring Boot REST API + Swing GUI + JNA를 이용해 Windows GP 프로그램과 WM_COPYDATA로 IPC하는 에이전트.

## 빌드

### macOS 로컬 개발 환경

JDK 17만 설치되어 있으면 프로젝트 전용 Maven과 의존성 캐시를 사용합니다.

```bash
./setup-local.sh
./build.sh
```

직접 Maven 명령을 실행할 때는 전역 `mvn` 대신 `./mvnw`를 사용합니다.

```bash
./mvnw test
./mvnw spring-boot:run
```

Windows EXE와 portable ZIP은 Windows에서 `build.bat`로 생성합니다.

### 일반 Maven 빌드

```bash
mvn clean package
```

빌드 결과물 (`target/` 디렉토리):

| 파일 | 설명 |
|---|---|
| `gp-api-1.0.0.jar` | Spring Boot 실행 가능 JAR (모든 기능 포함) |
| `GpApi.exe` | 본 API 서버 (Windows EXE) |
| `MockGp.exe` | 테스트용 Mock GP (Windows EXE) |

> EXE 실행에는 PC에 **JRE 17 이상**이 설치되어 있어야 합니다 (예: Adoptium Temurin 17).

## 실행

### 사내망 / 실제 GP 환경

1. 실제 GP 프로그램(`WndBroker_GP`)을 먼저 실행
2. `GpApi.exe` 실행 → Swing 창이 뜨고 GP 창을 자동으로 찾음
3. `POST http://localhost:8080/send/v1/account` 로 요청

```bash
curl -X POST http://localhost:8080/send/v1/account ^
  -H "Content-Type: application/json" ^
  -d "{\"pw\":\"1234\",\"account\":\"우리은행1002458969333\"}"
```

### 집 / 사외망 테스트 (Mock GP 사용)

1. `MockGp.exe` 먼저 실행 (Mock GP 창이 뜸)
2. `GpApi.exe` 실행 → Mock GP를 GP로 인식하고 HWND 등록
3. Mock GP 창에 "Java 에이전트 HWND 등록 완료" 로그가 찍히고 버튼 활성화
4. **API → GP 흐름**: 위 curl 호출 → Mock GP에 JSON이 수신됨
5. **GP → API 흐름**: Mock GP의 "계좌 정보 요청 보내기" 버튼 클릭 → 에이전트가 마지막 계좌를 다시 전송

### 비 Windows (개발용)

JAR을 직접 실행하면 macOS/Linux에서도 GUI와 API가 뜹니다. 단, JNA Win32 API는 호출되지 않고 **개발 모드 로그**만 찍힙니다.

```bash
java -jar target/gp-api-1.0.0.jar
```

## 프로젝트 구조

```
src/main/java/com/example/gpapi/
├── GpApiApplication.java         (Spring Boot 진입점, headless=false)
├── controller/AccountController.java  (POST /send/v1/account)
├── service/GpAgentService.java   (JNA 기반 Win32 IPC 로직)
├── dto/AccountRequest.java       (요청 본문)
├── dto/StepResult.java           (단계별 실행 결과)
├── event/LogEventBus.java        (서비스 → GUI 이벤트 전달)
├── ui/MainFrame.java             (Swing GUI)
└── mock/MockGp.java              (테스트용 가짜 GP)
```

## API 명세

### POST /send/v1/account

기존 GP 계좌 전송 후 GMSH(`GmshMainApp-CLASS`)에도 `SETACCTINFO`를 전송합니다.
GMSH 전송은 `WM_COPYDATA`, `dwData=91005` 규격을 사용하며 비밀번호는
`SimpleEncryptA(..., true)` 방식으로 암호화됩니다.

요청:
```json
{ "account": "우리은행1002458969333", "pw": "1234" }
```

응답 (성공):
```json
{ "result": "success" }
```

응답 (실패):
```json
{ "result": "fail", "message": "GP 창을 찾을 수 없습니다." }
```

## GUI 화면 구성

```
┌──────────────────────────────────────────────────────────────┐
│  서버 실행 중  ▶  POST http://localhost:8080/send/v1/account  │
├──────────────────────────┬───────────────────────────────────┤
│ 최근 API 요청 (Body)      │ 단계별 실행 결과                    │
│                          │ ┌──────┬─────────────┬────┬─────┐ │
│ [14:32:01]               │ │ 시간 │   단계      │결과│메시지│ │
│ {                        │ ├──────┼─────────────┼────┼─────┤ │
│   "account": "우리...",  │ │14:31│GP 창 검색   │성공│HWND=│ │
│   "pw": "****"           │ │14:31│HWND 등록    │성공│ ok  │ │
│ }                        │ │14:32│계좌 JSON전송│성공│ ... │ │
│                          │ └──────┴─────────────┴────┴─────┘ │
└──────────────────────────┴───────────────────────────────────┘
                                                  [ 로그 지우기 ]
```

## WM_COPYDATA 프로토콜

| dwData | 방향 | 의미 |
|---|---|---|
| 100 | Agent → GP | 내 HWND 등록 |
| 101 | GP → Agent | "계좌 정보 보내라" 요청 |
| 102 | Agent → GP | 계좌 JSON 응답 |
