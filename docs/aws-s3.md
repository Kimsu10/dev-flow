# DevFlow S3 로컬 개발 준비

이 설정은 `feat/blog`의 이미지 기능 구현 전 준비 단계입니다. 리전은 서울 `ap-northeast-2`, 로컬 AWS 프로파일은 `devflow-dev`입니다. 아직 S3 업로드 API나 이미지 테이블을 구현하지 않았습니다.

## 현재 설정

- AWS CLI v2 설치 확인: 2.40.4
- 루트 `.env.example`: `AWS_REGION`, `AWS_PROFILE`, `S3_BUCKET_NAME` 제공
- `scripts/start-backend.ps1`: 위 세 설정을 백엔드 환경변수로 전달
- AWS 자격증명은 `.env`나 코드 대신 AWS 프로파일의 임시 인증을 사용
- 사용자가 생성한 버킷: `devflow-images-151116838878-ap-northeast-2-an` (계정 리전 네임스페이스)
- 루트 `.env`의 `S3_BUCKET_NAME`에 위 버킷 이름 기록

## 인증 및 버킷 확인 결과 (2026-11-17)

- `aws login --profile devflow-dev --region ap-northeast-2` 성공
- STS 호출로 실제 IAM 사용자가 `devflow-agent`인 것을 확인
- 짧은 이름 `devflow-images`의 최초 `HeadBucket`은 404 Not Found 반환
- 초기 전체 이름의 버킷 접근은 403. 새 로그인 후에도 `ListObjectsV2`는 `s3:ListBucket` 허용 정책이 없다고 응답
- 사용자가 실제 정책 JSON을 확인한 결과 S3 Resource에 짧은 이름 `devflow-images`가 남아 있던 것이 원인
- 전체 버킷 이름으로 S3 Resource 두 곳을 수정한 후, 같은 `devflow-agent` 프로파일의 `HeadBucket` 성공
- 예상 소유 계정 일치 및 실제 버킷 리전 `ap-northeast-2` 확인
- 아래 실제 이미지 파일 작업 검증까지 완료

적용한 정책은 [전체 버킷 이름을 반영한 정책](aws/devflow-agent-policy.json)과 동일한 S3 Resource를 사용합니다. 파일 관리와 로그인 액션은 유지하고 S3 Resource 두 곳만 전체 버킷 이름으로 맞췄습니다. 파일 작업은 `devflow-agent` 프로파일로 검증했습니다.

### 실제 파일 검증 결과

검증 객체: `images/2026/11/bf80125f-b0c4-47af-beb9-4e736d014ac7.png` (68바이트 테스트 PNG, 검증 후 삭제)

| 항목 | 실제 결과 |
| --- | --- |
| 소유 계정·서울 리전 | 예상 소유 계정 조건을 지정한 HeadBucket 성공, `ap-northeast-2` |
| 업로드 | UUID key 및 조건부 업로드로 기존 파일 덮어쓰기 없이 성공 |
| 다운로드 | 원본과 SHA-256 체크섬 일치, Content-Type `image/png` |
| 객체 암호화 | 다운로드 응답의 `ServerSideEncryption=AES256` 확인 |
| 익명 접근 | 업로드 객체의 비인증 HEAD 요청 HTTP 403 |
| 삭제 | 생성한 테스트 객체만 삭제, 이후 HeadObject HTTP 404 |

기존 객체를 변경하거나 삭제하지 않았습니다. 위 결과는 CLI를 통한 로컬 인증과 S3 파일 작업 검증이며 블로그 업로드 API·DB 이미지 테이블·에디터는 아직 구현하지 않았습니다. 버킷 설정 조회 권한을 추가하지 않았으므로 퍼블릭 액세스 차단 네 항목 및 Object Ownership 설정값 자체는 CLI로 조회하지 않았습니다. 익명 객체 접근 차단과 객체 암호화는 실제 요청으로 검증했습니다.

## 이미지 저장 경로

이미지 업로드 기능 구현 시 `images/YYYY/MM/UUID.확장자`를 object key로 사용합니다. 연·월은 업로드 시각의 `Asia/Seoul` 기준이며, 원본 파일명은 DB에 별도로 저장합니다. 예: `images/2026/11/550e8400-e29b-41d4-a716-446655440000.png`. S3 콘솔에서는 이 접두사가 폴더처럼 표시되므로 빈 폴더 객체를 미리 생성하지 않습니다. 게시글 발행·수정 시 기존 이미지 key를 변경하지 않습니다.

`SignInLocalDevelopmentAccess`와 동등한 인라인 정책을 사용하는 경우 `signin:AuthorizeOAuth2Access`와 `signin:CreateOAuth2Token`을 모두 허용해야 합니다. `AuthorizeOAuth2Access`만 허용한 첫 시도는 토큰 생성 권한 부족으로 실패했으며, 두 권한을 적용한 재시도는 성공했습니다.

## 로컬 인증

PowerShell을 새로 열어 `aws --version`을 확인합니다. 설치 직후 기존 터미널에서는 아래 경로로 실행할 수 있습니다.

```powershell
$awsCli = Join-Path $env:LOCALAPPDATA 'Programs\Amazon\AWSCLIV2\aws.exe'
& $awsCli configure set region ap-northeast-2 --profile devflow-dev
& $awsCli configure set output json --profile devflow-dev
```

일반 AWS 콘솔의 IAM 사용자/역할로 로그인하는 경우:

```powershell
& $awsCli login --profile devflow-dev --region ap-northeast-2
```

브라우저의 로그인·MFA·승인은 계정 소유자가 직접 수행합니다. IAM 사용자/역할에는 AWS의 `SignInLocalDevelopmentAccess` 권한이 필요합니다. 이 권한은 로그인용이며 S3 접근 권한을 대신하지 않습니다. 루트 계정을 애플리케이션 실행 프로파일로 사용하는 대신 IAM 사용자 또는 역할을 사용합니다.

IAM Identity Center(SSO)를 사용하는 계정은 기존 SSO 시작 URL과 SSO 리전으로 `aws configure sso --profile devflow-dev`를 설정하고 `aws sso login --profile devflow-dev`로 로그인합니다. SSO 리전은 S3 버킷 리전과 다를 수 있습니다.

인증 확인:

```powershell
& $awsCli sts get-caller-identity --profile devflow-dev --region ap-northeast-2
```

Access Key, Secret Key, 세션 토큰, 로그인 승인 코드를 채팅이나 Git에 기록하지 않습니다. 로그인 캐시 파일도 저장소에 복사하지 않습니다. Java 이미지 기능 구현 시 AWS SDK for Java 2.x의 기본 자격증명 공급자를 사용합니다.

## 버킷 구성 방침

- 서울 리전의 개발 전용 일반 S3 버킷
- 고유한 이름 사용, 기존 버킷을 임의로 변경하지 않음
- 퍼블릭 액세스 차단 네 항목 모두 활성화
- Object Ownership은 Bucket owner enforced (ACL 비활성화)
- 기본 암호화는 SSE-S3
- 애플리케이션 권한은 해당 버킷의 이미지 경로로 제한
- 서버를 통한 업로드이므로 현재 단계에서는 브라우저 직접 업로드용 CORS 불필요
- 버킷 생성 및 실제 인증·업로드 검증 완료 전에는 S3 연동 완료로 간주하지 않음

최종 로컬 설정 예시:

```dotenv
AWS_REGION=ap-northeast-2
AWS_PROFILE=devflow-dev
S3_BUCKET_NAME=devflow-images-151116838878-ap-northeast-2-an
```

참고: [AWS CLI 설치](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html), [콘솔 로그인 인증](https://docs.aws.amazon.com/signin/latest/userguide/command-line-sign-in.html), [Java SDK 기본 인증](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html).
