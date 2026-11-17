# 실제 검증 기록 — 2026-11-17

Windows PowerShell, Python 3.10.5, Codex CLI 0.169.0, PostgreSQL `localhost:15432/devflow` 환경에서 실행했습니다. Webhook 주소와 인증정보는 기록하지 않습니다.

| 검증 | 결과 |
| --- | --- |
| Webhook 실제 연결 | `wait=true` 요청 HTTP 200, Discord 메시지 ID 기록 |
| 합성 성공 작업 | `e42dbab0-8aa1-4315-9d40-f31b5e3440e2`, 시작/완료 전송 2건 성공 |
| 합성 실패 작업 | `d69982c0-240e-4e86-b8da-000b38e8ae21`, 시작/실패 전송 2건 성공 |
| 실제 Codex CLI 작업 | `a1ed509f-79f5-42be-9f41-f3d4437628d9`, completed, 시작/완료 전송 2건 성공 |
| 실제 작업 DB 기록 | 이벤트 11건: 시작, 주요 Codex 이벤트, 직접 실행 검사 4건, 완료 |
| 직접 실행 검사 | Agent 테스트, frontend 테스트, frontend 빌드, backend 빌드/테스트 모두 exit 0 |
| 최종 Agent 자동 테스트 | 19개 통과 |
| DB 비밀정보 검사 | 저장한 이벤트 payload에서 현재 환경의 알려진 비밀값이 없음을 assertion으로 확인 |

첫 연결 테스트는 샌드박스의 외부 연결 제한으로 실패했습니다. DB에 네트워크 오류 3회와 최종 실패가 기록됐고, 외부 연결을 허용한 실제 실행은 1회 시도로 성공했습니다. HTTP 429 대기/5xx 재시도/401 즉시 실패/분할 일부 실패는 외부 서비스를 의도적으로 장애 내지 않고 모의 HTTP 응답으로 검증했습니다. 최대 3회 제한, Unicode 분할, 마스킹, 시간초과 후 프로세스 정리, 완료 이벤트 누락, 검사 실패, 전송 실패와 작업 상태 분리도 자동 테스트로 확인했습니다.

실제 Codex 작업은 README를 읽고 한 문장으로 요약하는 읽기 전용 작업입니다. 이 작업의 파일 변경 목록은 비어 있었고, 기존 작업 트리 변경은 Git 상태 및 diff 통계에 별도 표시했습니다. 합성 실패는 실제 Codex 장애로 오인하지 않도록 `[SYNTHETIC TEST]` 제목과 `verification_fixture=true`로 기록했습니다. 실제 Codex 서버 장애를 유발한 테스트는 수행하지 않았습니다.

PostgreSQL을 삭제·초기화하지 않았습니다. 새 `devflow_reporting` 스키마와 감사용 테이블만 추가했습니다. 기존 `compose.yaml`과 README 변경, 기존 알림 스크립트를 보존했으며 Git add/commit/push는 실행하지 않았습니다. Orca의 전역 훅/notify와 별도 Bot 승인 설정도 변경하지 않았습니다.

재현 명령과 운영상 한계는 [Agent README](README.md)를 참고하세요.
