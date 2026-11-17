# DevFlow Codex 작업 보고

DevFlow Agent는 **Codex 실행을 감싸는 Orchestrator**입니다. 프롬프트에 Discord 전송을 요청하는 방식이 아닙니다. Python 3.10+, 기존 Codex CLI 로그인, PostgreSQL과 Discord Webhook을 사용합니다. 블로그의 Java/Vue 애플리케이션과 독립적으로 실행됩니다.

## 기존 코드 조사

이 체크아웃과 접근 가능한 Desktop 경로에서 `agent/`, `discord-bot/` 기존 소스를 찾지 못했습니다. 확인한 기존 코드는 `scripts/notify_discord.py`입니다. 이 스크립트는 `agent-turn-complete`와 `last-assistant-message`를 받아 한 번의 Webhook 요청만 전송했고, 시작/실패 수집·재시도·DB 기록이 없었습니다.

기존 `get_webhook_url()`을 Orchestrator에서 재사용하고, 전송·마스킹·분할·재시도 코드를 공통 모듈로 옮겼습니다. 기존 스크립트의 호출 형식은 유지합니다. 관리 작업의 자식 프로세스에는 `DEVFLOW_ORCHESTRATED=1`을 전달하여 같은 스크립트가 완료 알림을 중복 전송하지 않게 합니다. 독립적인 기존 훅 호출은 종전처럼 완료 알림만 전송하며, DB 감사 기록은 Orchestrator 경로에서 제공합니다.

별도 Discord Bot의 명령 처리, 승인 버튼, 큐, 토큰, Gateway 연결은 추가하거나 변경하지 않습니다. 다른 위치에 Bot 소스가 있다면 승인 완료 핸들러에서 아래 `run` 명령을 호출해야 합니다. 승인되지 않은 작업을 자동 실행시키는 연결은 포함하지 않았습니다. 승인 핸들러가 작업을 실행할 때 들어가는 보고 경계로 사용합니다.

## 설치 (저장소 루트, PowerShell)

```powershell
python -m venv agent/.venv
agent/.venv/Scripts/python.exe -m pip install -r agent/requirements.txt
agent/.venv/Scripts/python.exe -m agent.cli init-db
agent/.venv/Scripts/python.exe -m agent.cli webhook-test
```

루트 `.env`에 기존 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT=15432`, `DISCORD_WEBHOOK_URL`을 설정합니다. 파일은 수정하거나 출력하지 않습니다. OS 환경변수가 `.env`보다 우선합니다. URL은 로드 후 환경변수에서만 읽습니다.

Agent는 `POSTGRES_HOST` (기본 localhost), `POSTGRES_PORT` (기본 15432), `POSTGRES_DB` (기본 devflow), `POSTGRES_USER` (기본 devflow), `POSTGRES_PASSWORD`를 사용합니다. 인증에는 `DB_USERNAME`, `DB_PASSWORD`가 설정되어 있으면 우선 사용합니다. Java 전용 `DB_URL`은 Agent에서 사용하지 않습니다. 기존 Compose 기본값과 다를 수 있으므로 실제 호스트 포트를 명시하세요.

Codex 실행 파일은 `CODEX_EXECUTABLE` → Orca의 `CODEX_MANAGED_PACKAGE_ROOT/bin/codex.js` → PATH의 Codex 순으로 찾습니다. `CODEX_EXECUTABLE`에는 `.exe`나 `bin/codex.js`의 절대경로를 지정합니다. `.js` 실행에는 PATH에 Node.js가 있어야 합니다. 예:

```powershell
$env:CODEX_EXECUTABLE = "$env:APPDATA/npm/node_modules/@openai/codex/bin/codex.js"
```

기존 로그인은 그대로 사용합니다. 새 로그인·전역 Codex 설정·Orca 훅·notify 설정 변경을 자동으로 실행하지 않습니다.

## 실제 작업 실행

Orca의 PowerShell 탭에서 직접 `codex` 대신 다음 명령을 사용합니다.

```powershell
agent/.venv/Scripts/python.exe -m agent.cli run --title "작업 제목" --prompt "구현할 작업 내용" --checks all
```

긴 작업 내용은 비밀정보 없는 UTF-8 파일로 저장한 뒤 `--prompt-file task.txt`로 전달할 수 있습니다. 프롬프트 원문은 DB나 Discord에 기록하지 않습니다. 실제 개발은 기본 `workspace-write` 샌드박스를 사용합니다. 조사만 할 때는 `--sandbox read-only`를 선택합니다. Codex는 `exec --json`으로 실행되며 기존 권한·승인 정책을 완화하거나 우회하는 옵션은 사용하지 않습니다.

`--checks none`은 검사를 실행하지 않습니다 (기본값). Discord에는 실패한 검사만 표시하며, 성공했거나 미실행인 경우 빌드/테스트 항목을 생략합니다. 전체 검사 결과는 PostgreSQL에 기록합니다. `agent`는 Agent 테스트, `app`은 Vue 테스트·빌드와 Gradle 빌드/테스트, `all`은 모두 실행합니다. 앱 의존성 설치는 별도로 frontend에서 `npm ci`를 실행해야 합니다. 검사 결과는 Codex의 자기 보고가 아니라 Orchestrator가 실행한 프로세스 종료 코드입니다. Codex가 실행한 임의 명령을 테스트 통과로 간주하지 않습니다.

`--timeout`은 Codex 실행 시간 제한이며 기본 1800초입니다. 개별 검사 제한은 최대 600초입니다. 실행 실패, 완료 이벤트 누락, 시간초과, 중단, 검사 실패는 실패로 보고합니다. `turn.completed`와 종료 코드 0을 모두 확인해야 완료로 처리합니다.

## 보고·DB 기록

```text
run → DB task/running + 시작 이벤트 → Discord 시작 보고
    → codex exec --json → 주요 Codex 이벤트 기록
    → 선택된 빌드/테스트 → Git 메타데이터 수집
    → DB completed/failed + 종료 이벤트 → Discord 완료/실패 보고
```

시작 알림에는 제목과 UUID를 포함합니다. 종료 보고에는 UUID·제목·최종 Codex 요약·작업 중 바뀐 파일·현재 Git diff 통계·실패한 빌드/테스트·적용 브랜치·오류 요약을 포함합니다. Discord에는 상세 Git 상태 대신 브랜치 이름만 표시합니다. 작업 시작 전 파일 지문과 종료 후 지문을 비교하므로 기존 미커밋 파일을 모두 새 작업 변경으로 오인하지 않습니다. 같은 디렉터리를 다른 프로세스가 동시에 수정하면 정확한 작성자 분리는 불가능합니다. Git 상태와 diff 통계는 **기존 변경을 포함한 전체 작업 트리 기준**이며, untracked 파일 내용은 diff 통계에 포함되지 않습니다. 민감 파일은 작업 파일 목록에서 제외합니다.

DB는 기존 DB 안에 다음 테이블만 추가합니다. `DROP`, `TRUNCATE`, 기존 데이터 삭제는 실행하지 않습니다.

| 테이블 | 기록 |
| --- | --- |
| `devflow_reporting.tasks` | 작업 UUID, 제목, running/completed/failed, 시작/종료 시각, 정제된 결과 JSON |
| `devflow_reporting.events` | 시작/완료/실패, 주요 Codex 이벤트, 검사 결과, 보고 본문, 전송 상태 |
| `devflow_reporting.delivery_attempts` | 이벤트·분할 번호·시도 횟수, 성공/재시도/실패, HTTP 상태, Discord 메시지 ID |

```powershell
agent/.venv/Scripts/python.exe -m agent.cli inspect
agent/.venv/Scripts/python.exe -m agent.cli inspect --task-id "작업 UUID"
```

전송할 이벤트를 DB에 먼저 기록하고 Discord 요청 이후 매 시도 결과를 남깁니다. DB 초기 연결 실패 시 작업은 시작하지 않습니다. 전송 실패 자체는 Codex 성공 여부와 분리되며 CLI 종료 코드 2로 표시합니다 (작업 실패는 1, 모두 성공은 0). 재시작 시 미완료 작업 재개나 pending 이벤트 자동 복구는 구현하지 않았습니다. 강제 프로세스 종료/DB 중단 시 running/pending 기록이 남을 수 있습니다.

## 전송 제한·보호

- HTTPS Discord Webhook만 허용하고 리다이렉트를 따라가지 않습니다.
- `wait=true` 응답으로 실제 Discord 메시지 ID를 기록합니다. 메시지는 보수적으로 UTF-16 2,000 단위 이내로 분할하고 이벤트 ID와 분할 순서를 붙입니다.
- 네트워크 오류·HTTP 429·5xx는 **최초 포함 최대 3회** 시도합니다. 429는 `retry_after`를 따르며 30초를 넘는 대기 지시는 조기 재전송하지 않고 실패로 기록합니다. 400/401/403/404는 즉시 실패합니다.
- 네트워크 타임아웃 직전에 서버가 메시지를 받았다면 재시도로 중복이 생길 수 있습니다. 이벤트 ID로 구분하며 exactly-once를 보장하지 않습니다.
- 모든 멘션과 링크 미리보기를 끕니다. Bot 승인 명령이나 인터랙션을 보내지 않습니다.
- `.env`의 알려진 비밀값, 환경변수의 인증정보, Webhook URL/토큰, Bearer/Bot 토큰, JWT, 비밀번호 할당, private key를 DB 저장·전송 전에 제거합니다. 전체 Git patch, 원시 명령어·명령 출력, 프롬프트를 보고하지 않습니다. 검사 출력도 전송하지 않고 결과 상태만 전달합니다.
- 자식 Codex에는 보고용 Webhook과 DB 비밀번호 환경변수를 전달하지 않습니다. 로컬 `.env` 파일 접근 자체를 OS 수준으로 차단하는 기능은 아니며 비밀정보를 작업 내용에 넣으면 안 됩니다. 임의의 알려지지 않은 무표식 문자열을 모두 비밀값으로 판별하는 DLP 기능은 아닙니다.

## Orca 수동 세션 조사 결과

현재 로컬 Codex CLI는 0.169.0이며 `exec --json`을 지원합니다. Orca의 기존 전역 훅에는 SessionStart, UserPromptSubmit, PreToolUse, PermissionRequest, PostToolUse, Stop, Interrupt 등이 설정되어 있습니다. 이 파일들과 기존 notify는 수정하지 않았습니다.

공식 [비대화형 실행 문서](https://developers.openai.com/codex/noninteractive)는 실행한 프로세스의 JSONL 이벤트를 제공하고, [훅 문서](https://developers.openai.com/codex/hooks)는 새 세션의 수명주기에 콜백을 연결하는 방법을 제공합니다. 수동 세션도 사전에 신뢰된 훅을 구성하면 일부 이벤트를 수집할 수 있지만, 이미 시작된 임의 Orca 세션에 외부 Agent가 붙어 시작 전 Git 상태·전체 이벤트·빌드 결과를 자동으로 얻는 공개 인터페이스는 확인하지 못했습니다. Orca 내부 환경변수·토큰이나 Codex 내부 SQLite/롤아웃 감시는 사용하지 않습니다.

따라서 이번 구현은 **Agent를 통해 시작한 작업만 자동 보고**합니다. 이미 진행 중인 세션을 소급 수집하지 않습니다. 향후 별도 훅 통합은 기존 Orca/Bot 승인 흐름을 확인한 뒤 진행해야 합니다.

## 검증 명령

```powershell
# 외부 전송 없는 단위·프로세스 테스트
agent/.venv/Scripts/python.exe -m unittest discover -s agent/tests -v
# 실제 Webhook 연결 테스트 (채팅방에 테스트 메시지 전송)
agent/.venv/Scripts/python.exe -m agent.cli webhook-test
# 합성 자식 프로세스로 시작/완료/실패 검증 (실제 Codex 실행이 아님을 명시)
agent/.venv/Scripts/python.exe -m agent.cli smoke --scenario all
# 실제 Codex 읽기 전용 실행 및 전체 검사, 완료 알림
agent/.venv/Scripts/python.exe -m agent.cli run --title "보고 검증" --prompt "README.md만 읽고 프로젝트 구성을 한 문장으로 요약하세요. 파일 수정과 비밀정보 접근은 하지 마세요." --sandbox read-only --checks all --timeout 600
agent/.venv/Scripts/python.exe -m agent.cli inspect
```

Discord Webhook API와 길이 제한: [공식 문서](https://discord.com/developers/docs/resources/webhook#execute-webhook). 재시도 대기: [공식 rate-limit 문서](https://discord.com/developers/docs/topics/rate-limits).
