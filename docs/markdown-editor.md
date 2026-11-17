# DevFlow Markdown Editor

## 기존 구성과 범위

- Vue 3.5, Vite 8, TypeScript 6. UI 라이브러리와 Pinia는 없으며 페이지 분리를 위해 Vue Router 4를 추가.
- `/`는 HomeView의 기존 `/api/health` 상태 확인과 글 작성 링크, `/posts/create`는 PostCreateView의 작성 화면.
- 작성 페이지는 지연 로딩하며 제목·본문·카테고리·태그가 모두 빈 값으로 시작한다. 본문의 `글을 작성하세요`는 실제 원문에 포함되지 않는 placeholder다.
- 카테고리는 자유 입력, 태그는 쉼표 구분 입력이며 공백·빈 항목과 중복을 제외한 태그를 표시한다. 메타데이터는 페이지 상태로 관리하고 `metadata` 슬롯으로 배치하여 에디터의 독립성을 유지한다. 서버 저장은 아직 연결하지 않았다.
- Spring Boot 3.5 / Java 21의 health Controller → Service → MyBatis Mapper 유지.
- 환경변수 기반 PostgreSQL 연결, 개발 `/api` Vite 프록시 유지.
- 이미지 업로드 및 게시글 API가 없으므로 이번 변경은 프런트엔드만 구현.

## 컴포넌트

`frontend/src/components/editor/`:

| 파일 | 역할 |
| --- | --- |
| MarkdownEditorLayout.vue | 제목, 편집 모드, 전체 화면, 저장/발행 이벤트, 스크롤 동기화 |
| MarkdownEditor.vue | CodeMirror 6 원문 v-model, 키맵, 이미지 선택/드롭/붙여넣기 |
| MarkdownToolbar.vue | 선택 영역에 적용하는 서식과 H1~H6 선택 |
| MarkdownPreview.vue | 안전한 HTML 미리보기와 스크롤 인터페이스 |
| format.ts | CodeMirror 서식 트랜잭션 |
| markdown.ts | markdown-it + task lists + highlight.js + DOMPurify |
| image.ts | 파일 크기/형식 검증 및 안전한 Markdown 이미지 생성 |
| types.ts | EditorMode, Format, UploadImage 계약 |
| vendor.d.ts | task-list 플러그인 타입 선언 |
| editor.css | 반응형 레이아웃, CSS 테마 변수, 미리보기 스타일 |
| editor.test.ts / components.test.ts | 렌더링·보안·서식·컴포넌트·업로드 테스트 |

레이아웃은 Editor, Toolbar, Preview를 조합한다. 입력 컴포넌트를 단독으로 가져와 `v-model`로 사용할 수도 있다. 뷰 전환은 입력 컴포넌트를 유지하여 선택과 Undo 기록을 보존한다.

## 사용 예제

```vue
<script setup lang="ts">
import { ref } from 'vue'
import MarkdownEditorLayout from './components/editor/MarkdownEditorLayout.vue'
import type { UploadImage } from './components/editor/types'

const title = ref('')
const markdown = ref('# 새로운 글')

// 서비스 계층에서 구현할 어댑터를 부모가 주입한다.
const props = defineProps<{ upload: UploadImage }>()
function saveDraft() {
  // 부모에서 title.value, markdown.value를 저장한다.
}
function publish() {
  // 부모에서 입력 검증과 발행 API를 처리한다.
}
</script>

<template>
  <MarkdownEditorLayout
    v-model="markdown"
    v-model:title="title"
    :upload-image="props.upload"
    :max-image-bytes="5 * 1024 * 1024"
    @save="saveDraft"
    @publish="publish"
  />
</template>
```

`UploadImage`는 `(file: File, onProgress: (percent: number) => void) => Promise<{ url: string; alt?: string }>`이다. 진행률은 0~100 값을 전달한다. 실제 전송 진행률을 제공하는 XHR 등은 부모 어댑터가 선택한다. Promise 완료 후 HTTP(S) URL을 Markdown으로 삽입하며, 상대 URL은 현재 origin으로 해석한다. 실패는 Error로 reject한다. 여러 파일은 순차 처리하며 이전에 성공한 파일은 유지한다. 업로드 중 추가 업로드는 받지 않는다. 업로드 완료 전에 컴포넌트가 제거되면 결과를 삽입하지 않는다(네트워크 취소는 업로드 어댑터 책임).

업로드 함수를 생략하면 이미지 버튼은 URL 문법을 삽입하고 파일 연동 안내를 표시한다. 파일 드롭·붙여넣기는 업로더 미연결 안내를 표시한다. SVG는 허용하지 않는다. 파일 크기 기본 제한은 5MB이며 PNG/JPEG/GIF/WebP의 확장자, MIME과 시작 시그니처를 확인한다. 이 검증은 완전한 이미지 디코딩 검증이 아니므로 향후 서버에서도 크기 제한, 이미지 디코딩/재인코딩, MIME 검증, 안전한 저장 키와 접근 제어가 필요하다. 실제 파일 저장소와 API 경로는 아직 결정하지 않았다.

`actions` 슬롯으로 기본 저장/발행 버튼을 교체할 수 있다. 슬롯 props의 `uploading`을 사용해 부모 버튼을 비활성화할 수 있다. 에디터 내부에는 저장/발행 API 호출이나 자동 저장이 없다. 작성 페이지의 버튼은 API 미연결 안내만 표시한다. 원문 placeholder는 `placeholder` prop으로 변경할 수 있다.

## URL 직접 접속과 배포

개발 서버에서 `http://localhost:5173/posts/create`에 직접 접속할 수 있다. Vue Router의 HTML5 history를 사용하므로 배포 서버는 `/posts/create` 등 프런트엔드 경로를 `index.html`로 fallback해야 한다. Nginx라면 프런트엔드 location에서 `try_files $uri $uri/ /index.html;`을 사용한다. `/api`는 별도 location에서 기존 동일 출처 백엔드 프록시를 유지한다. 실제 배포 설정은 이번 변경에 포함되지 않는다.

## 제공 기능

- H1~H6, Bold, Italic, Strikethrough, Quote, 순서/비순서/Task 목록, 링크, 이미지, 표, 수평선, 인라인/펜스 코드.
- Java, JavaScript, TypeScript, SQL, JSON, Bash 미리보기 하이라이팅. 알 수 없는 언어는 이스케이프된 일반 코드로 표시.
- 툴바 선택 영역 적용, Ctrl/⌘+B·I, 기본 Undo/Redo와 Ctrl/⌘+Shift+Z, Tab/Shift+Tab 들여쓰기.
- Editor Only / Split View / Preview Only. 760px 이하 Split은 상하 배치.
- 편집기와 미리보기의 상대 스크롤 비율 동기화. 문단 단위 정확한 매핑은 제공하지 않음.
- 전체 화면 작업 공간과 Escape 종료. 테마 확장을 위한 `--md-*` 변수(별도의 라이트/다크 전환 UI는 없음).
- 원문 HTML 비활성화 및 DOMPurify HTML 프로필 적용. 이벤트 핸들러와 위험 URL 차단.

## 검증 및 후속 작업

프런트엔드: `npm ci`, `npm test`, `npm run build` (Windows는 `npm.cmd`). 테스트는 기존 상태 확인, Markdown/XSS, 주요 언어, 서식 선택과 Undo/Redo, 부모 v-model, 모드, 제목, 이벤트, 업로드 진행/실패/편집 중 위치 보정을 포함한다.

백엔드: `.\gradlew.bat build`. 현재 통합 테스트는 H2 PostgreSQL 모드를 사용하므로 PostgreSQL 실연동을 대신하지 않는다. 이번 실행에서는 Docker CLI를 찾지 못해 Docker/PostgreSQL 실연동을 검증하지 못했다. 브라우저 연결 호스트도 사용할 수 없어 실제 화면과 모바일/키보드/스크롤 시각 검수는 미완료다.

다음 단계는 실제 브라우저 검수, 이미지 저장 방식과 업로드 API 계약 확정, 부모 어댑터 연결이다. 이후 별도 범위에서 게시글 CRUD/임시 저장/인증/발행을 구현한다. 작성 라우트는 지연 로딩하여 메인 접속 시 에디터를 로딩하지 않는다.
