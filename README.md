# SHK TOWN Core

SHK TOWN의 공통 기능과 서버 전용 게임 로직을 구현하는 Paper 플러그인입니다.

기성 플러그인의 기능을 활용하고, 서버 고유의 규칙과 플러그인 간 연동을 담당합니다.

## Repositories

| Repository | 용도 |
| --- | --- |
| [shk-town-core](https://github.com/SHK-development/shk-town-core) | Java 소스, 빌드 설정, 기본 설정, DB 스키마 |
| [shk-town-server](https://github.com/SHK-development/shk-town-server) | 서버 설정, Skript, 플러그인 구성, 운영 문서 |

## Tech Stack

| 항목 | 환경 |
| --- | --- |
| Language | Java 25 |
| Server | Paper 26.3 |
| Build | Gradle Wrapper |
| Database | MySQL / HikariCP |
| Economy | VaultUnlocked Legacy API / EssentialsX Economy |

외부 플러그인 의존성과 버전은 `build.gradle.kts`와 `plugin.yml`을 기준으로 확인합니다.

## Getting Started

1. 저장소를 클론하고 IntelliJ IDEA에서 Gradle 프로젝트로 엽니다.
2. Project SDK와 Gradle JVM을 Java 25로 설정합니다.
3. MySQL과 테스트 서버를 준비합니다.
4. 서버 설정에 DB 접속 정보를 입력합니다.
5. 빌드 후 테스트 서버를 재시작합니다.

### Build

Windows:

```powershell
.\gradlew.bat build
```

macOS / Linux:

```bash
./gradlew build
```

현재 Shadow JAR 출력 경로:

```text
../test_server/plugins/SHKTown.jar
```

테스트 서버를 종료한 상태에서 빌드합니다. 동일 플러그인의 JAR가 중복으로 들어가지 않도록 확인합니다.

### Configuration

- 기본 설정: `src/main/resources/`
- 실행 환경 설정: 테스트 서버의 `plugins/SHKTown/`
- 설정 재적용: `/shktown reload`
- Java 코드 및 의존성 변경: 빌드 후 서버 재시작

`/shktown reload`로 적용되는 범위는 해당 명령의 구현을 기준으로 합니다.

DB 비밀번호, 토큰, 결제 인증 정보 등은 커밋하지 않습니다.

## Development Guidelines

### Implementation

- 가격, 좌표, 시간, 메시지 등 운영 값은 외부 설정으로 관리합니다.
- 개인별 상태와 거래 내역은 DB에 저장합니다.
- 기존 플러그인 도입 여부는 기능 적합성, 호환성, 설정 비용, 유지보수 비용으로 판단합니다.
- 동일한 데이터를 여러 시스템에서 중복 관리하지 않습니다.
- 미정인 게임 규칙은 구현 전에 확인합니다.
- 리팩토링 PR에는 변경 이유와 영향을 기록합니다.

### Threading

- 월드, 엔티티, 인벤토리와 Vault 경제 처리는 서버 스레드에서 실행합니다.
- DB 및 외부 API 요청은 서버 스레드를 차단하지 않도록 처리합니다.
- 비동기 작업의 결과를 게임에 반영할 때는 서버 스레드로 전환합니다.

### Transactions

- 출금, 아이템 지급, 권한 변경은 실패와 중복 요청을 고려합니다.
- DB 트랜잭션만으로 Vault와 인벤토리 변경까지 원자적으로 처리된다고 가정하지 않습니다.
- 크레딧 및 결제 연동은 중복 지급 방지와 처리 내역 추적을 포함합니다.

## Workflow

개발 기준 브랜치는 `develop`입니다.

1. 최신 `develop`에서 작업 브랜치를 생성합니다.
2. 변경 사항을 구현하고 검증합니다.
3. 커밋·푸시 후 `develop` 대상으로 PR을 생성합니다.
4. 검토 후 병합하고 작업 브랜치를 삭제합니다.

### Branch Naming

| Prefix | 용도 | 예시 |
| --- | --- | --- |
| `feat/` | 기능 추가 | `feat/taxi-travel` |
| `fix/` | 오류 수정 | `fix/vault-provider` |
| `refactor/` | 구조 개선 | `refactor/taxi-config` |
| `docs/` | 문서 수정 | `docs/readme` |
| `chore/` | 빌드·개발 환경 정리 | `chore/update-dependencies` |

### Commit Message

```text
<type>: <변경 내용>
```

예시:

```text
feat: NPC 택시 이동 기능 추가
fix: Vault Legacy 경제 제공자 조회 수정
docs: 개발 환경과 협업 절차 정리
```

### Pull Request

PR에는 다음 내용을 간단히 작성합니다.

- 변경 목적과 주요 내용
- 검증 방법 및 결과
- 설정·DB 변경 여부
- 함께 적용해야 하는 서버 설정 PR
- 남아 있는 제한 사항

## Verification

변경 범위에 맞춰 빌드와 테스트 서버 동작을 확인합니다.

- 정상 동작
- 권한 부족 및 잘못된 설정
- 잔액·아이템 부족 등 실패 조건
- 데이터 변경 시 재접속·재시작 후 유지 여부
- 거래 처리 시 중복 클릭 및 실패 후 상태

검증하지 않은 항목은 PR에 명시합니다.
