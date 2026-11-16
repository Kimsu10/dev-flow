# DevFlow 작업 지침

- Vue 3 + Vite + TypeScript, Java 21 + Spring Boot 3 + Gradle, PostgreSQL + MyBatis를 사용한다.
- 백엔드는 Controller → Service → Mapper 계층을 유지한다. Controller에서 SQL을 실행하지 않는다.
- 환경별 설정은 환경변수로 주입한다. 비밀번호·토큰을 코드, 로그, 테스트 결과에 기록하지 않는다.
- `.env`는 로컬 전용이며 추적하지 않는다. 예시는 비밀값 없는 `.env.example`에 작성한다.
- 기존 파일과 사용자 변경을 보존하고 요청 범위 밖 기능은 추가하지 않는다.
- 사용자 지시 없이 `git add`, `git commit`, `git push` 및 GitHub 원격 저장소 변경을 실행하지 않는다.
- 검증: frontend에서 `npm ci`, `npm test`, `npm run build`; backend에서 `./gradlew build` (Windows: `.\gradlew.bat build`).
- H2 테스트는 PostgreSQL 실연동을 대체하지 않는다. Docker 검증 불가 시 명확히 보고한다.
- 프런트엔드 `/api`는 개발 시 Vite 프록시, 배포 시 동일 출처 리버스 프록시를 사용한다.
