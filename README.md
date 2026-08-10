# GP API Agent

로컬 REST API 요청을 받아 Windows의 GP와 GMSH 프로그램에 계좌 정보를 전달하는 데스크톱 에이전트입니다.
Spring Boot, Swing, JNA를 사용하며 Windows 프로그램과는 `WM_COPYDATA`로 통신합니다.

## 주요 기능

- `POST /send/v1/account` 계좌정보 전송 API
- GP 또는 Mock GP 자동 검색 및 HWND 등록
- GMSH `SETACCTINFO` 동시 전송
- GMSH 전송 전 계좌 비밀번호 암호화
- 요청 내용과 단계별 전송 결과를 보여주는 Swing GUI
- Windows 실행 파일 및 JRE 포함 portable ZIP 빌드

## 요구 환경

- 빌드: JDK 17 이상
- 실행: Windows 64-bit 권장
- 로컬 API 주소: `http://127.0.0.1:8080`

API 서버는 보안을 위해 `127.0.0.1`에만 바인딩됩니다. 같은 PC에서 실행되는 프로그램만 호출할 수 있습니다.

## 빌드

### macOS 로컬 개발

JDK 17이 설치되어 있으면 프로젝트 전용 Maven 3.9.11과 독립 의존성 캐시를 사용합니다.

```bash
./setup-local.sh
./build.sh
```

일반 Maven 명령도 프로젝트 래퍼로 실행할 수 있습니다.

```bash
./mvnw test
./mvnw spring-boot:run
```

macOS 빌드의 주 산출물은 `target/gp-api-1.0.0.jar`입니다.

### Windows

JDK 17과 Maven이 설치된 명령 프롬프트에서 실행합니다.

```bat
build.bat
```

빌드 산출물:

| 파일 | 설명 |
|---|---|
| `target/gp-api-1.0.0.jar` | 모든 OS에서 실행 가능한 Spring Boot JAR |
| `target/GpApi.exe` | Windows용 API 서버 및 GUI |
| `target/MockGp.exe` | Windows용 테스트 GP |
| `target/gp-api-portable.zip` | 실행 파일과 전용 JRE가 포함된 배포 패키지 |

## 실행

### Windows 운영 환경

1. GP 프로그램과 GMSH 프로그램을 실행합니다.
2. `GpApi.exe`를 실행합니다.
3. GUI에서 연결 상태를 확인합니다.
4. 같은 PC에서 계좌정보 API를 호출합니다.

### JAR 직접 실행

```bash
java -jar target/gp-api-1.0.0.jar
```

macOS와 Linux에서도 GUI와 API 서버는 실행되지만 Win32 통신은 비활성화됩니다.

### Mock GP 테스트

1. `MockGp.exe`를 실행합니다.
2. `GpApi.exe`를 실행합니다.
3. Mock GP에 `Java 에이전트 HWND 등록 완료` 로그가 표시되는지 확인합니다.
4. API를 호출하고 Mock GP의 수신 로그를 확인합니다.

## API

### 계좌정보 전송

```text
POST /send/v1/account
Content-Type: application/json
```

요청 본문:

```json
{
  "account": "계좌번호",
  "pw": "계좌비밀번호"
}
```

Windows CMD 호출 예시:

```bat
curl -X POST http://127.0.0.1:8080/send/v1/account ^
  -H "Content-Type: application/json" ^
  -d "{\"account\":\"계좌번호\",\"pw\":\"계좌비밀번호\"}"
```

macOS/Linux 호출 예시:

```bash
curl -X POST http://127.0.0.1:8080/send/v1/account \
  -H 'Content-Type: application/json' \
  -d '{"account":"계좌번호","pw":"계좌비밀번호"}'
```

성공 응답:

```json
{
  "result": "success",
  "mode": "PROD"
}
```

실패 응답:

```json
{
  "result": "fail",
  "mode": "PROD",
  "message": "실패 원인"
}
```

호출 한 번에 다음 전송을 순서대로 수행합니다.

1. GP에 계좌 JSON 전송
2. GMSH에 `SETACCTINFO` JSON 전송

두 단계 중 하나라도 실패하면 API는 HTTP 500과 실패 응답을 반환합니다.

## 통신 규격

### GP

대상 창:

- Class name: `WGToSH`
- Window title: `WndBroker_GP`

| dwData | 방향 | 의미 |
|---|---|---|
| `100` | Agent → GP | Agent HWND 등록 |
| `101` | GP → Agent | 계좌정보 요청 또는 연결 응답 |
| `102` | Agent → GP | 계좌 JSON 전송 |

### GMSH

대상 창:

- Class name: `GmshMainApp-CLASS`
- Window title: 지정하지 않음
- `dwData`: `91005`
- 문자열 인코딩: UTF-8
- `cbData`: NULL 종료 문자를 제외한 JSON byte 길이

전송 JSON:

```json
{
  "command": "SETACCTINFO",
  "acct_no": "계좌번호",
  "acct_pwd": "암호화된 비밀번호"
}
```

`acct_pwd`는 상대 프로그램의 `SimpleEncryptA(value, true)` 규격으로 암호화한 값입니다. 평문 비밀번호는 GMSH JSON에 포함하지 않습니다.

## 프로젝트 구조

```text
src/main/java/com/example/gpapi/
├── GpApiApplication.java
├── controller/
│   └── AccountController.java
├── dto/
│   ├── AccountRequest.java
│   ├── RequestLog.java
│   └── StepResult.java
├── event/
│   └── LogEventBus.java
├── inspector/
│   └── WindowInspector.java
├── mock/
│   └── MockGp.java
├── service/
│   ├── GmshAccountService.java
│   ├── GpAgentService.java
│   └── RuntimeMode.java
├── startup/
│   └── StartupManager.java
└── ui/
    └── MainFrame.java
```

## 보안 참고사항

- API는 루프백 주소에만 노출됩니다.
- GUI와 로그에는 비밀번호가 마스킹되어 표시됩니다.
- 저장소에 실제 계좌번호나 비밀번호를 커밋하지 마세요.
- 운영 배포 전 Windows 실행 파일의 코드 서명을 권장합니다.
