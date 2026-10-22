# InsuranceAI — 하네스 엔지니어링

이 디렉터리는 [revfactory/harness](https://github.com/revfactory/harness/blob/main/README_KO.md) 의 하네스 엔지니어링 방법론을 InsuranceAI 프로젝트에 적용한 컨텍스트 팩토리다.
도메인 설명을 "에이전트 팀 + 스킬 + 폴더/기능별 컨텍스트"로 변환해, AI 에이전트가 이 코드베이스에서 바로 작업할 수 있게 한다.

## 6단계 워크플로우 매핑

| Phase | 하네스 원문 | 이 프로젝트에서 |
|---|---|---|
| 1 | 도메인 분석 | [00-domain.md](00-domain.md) |
| 2 | 팀 아키텍처 설계 | [01-architecture.md](01-architecture.md) |
| 3 | 에이전트 정의 생성 | [agents/](agents/) |
| 4 | 스킬 생성 | [modules/](modules/) · [features/](features/) (폴더/기능별 컨텍스트) |
| 5 | 통합·오케스트레이션 | [02-workflow.md](02-workflow.md) · [conventions.md](conventions.md) |
| 6 | 검증·테스트 | 각 모듈 문서의 "테스트" 절 · [agents/qa.md](agents/qa.md) |

## 아키텍처 패턴 (이 프로젝트 채택)

- **Generate-Validate** — 모든 기능은 TDD RED(테스트 먼저) → GREEN(구현) → 검증(로컬 구동) 게이트를 통과한다.
- **Pipeline** — 청구 협업이 단계형 상태 머신(OPEN→…→CLOSED)으로 진행된다.
- **Expert Pool** — 역할(CLAIMANT / ADJUSTER)에 따라 노출 기능이 달라진다.

## 문서 인덱스

전체 목록은 [INDEX.md](INDEX.md) 참조.

### 상위
- [00-domain.md](00-domain.md) — 도메인 분석
- [01-architecture.md](01-architecture.md) — 시스템·모듈 아키텍처
- [02-workflow.md](02-workflow.md) — 개발 워크플로우
- [conventions.md](conventions.md) — 코드·API·보안 규약

### 모듈 컨텍스트 (`modules/`)
폴더 하나 = 문서 하나. 그 폴더에서 작업할 때 읽는다.

### 기능 컨텍스트 (`features/`)
엔드투엔드 시나리오 하나 = 문서 하나. 여러 모듈을 관통하는 흐름을 설명한다.

### 에이전트 (`agents/`)
`.claude/agents/*.md` 형식. 역할별 작업 방식과 가드레일.

## 모듈 문서 템플릿

각 `modules/*.md` 는 다음 절을 갖는다:

```
## 목적          — 이 모듈이 책임지는 것 / 책임지지 않는 것
## 경로          — 소스/테스트 디렉터리
## 핵심 타입      — 엔티티·서비스·포트·엔드포인트
## 불변식·규칙    — 도메인 규칙, 상태 전이, 검증
## 의존 관계      — 들어오는/나가는 의존 (모듈 경계)
## 테스트         — 위치, 실행 명령, 커버 범위
## 주의 (gotchas) — 함정, 알려진 제약
## 상태·TODO      — 완료 여부, 다음 작업
```
