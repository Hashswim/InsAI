# InsuranceAI

AI 중립 청구 협업 플랫폼 — 청구인과 손해사정사가 같은 공간에서 소통하고,
그 사이를 AI가 중개(서류 구조화 · 요약/하이라이트/누락 탐지 · 약관 근거 평문 번역)한다.

## 구조

```
InsuranceAI/
├── back/
│   └── insai/     Spring Boot 백엔드
└── front/
    └── insai/     Vue 3 SPA (JavaScript)
```

> 향후 MSA로 분리 예정: gateway / auth / collab(청구 협업 코어) / form(양식·제출물) / document / ai(LLM 오케스트레이션)

## 기술 스택

- Backend: Java, Spring Boot, Gradle
- Frontend: Vue 3, Vite, JavaScript, Pinia, Vue Router
- Infra: Docker Compose (로컬)

## 브랜치 전략

- `main` — 배포 기준
- `dev` — 통합 개발
- `test` — QA / 테스트

## 개발

작성 예정.
