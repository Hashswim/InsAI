# 모듈: back/insai · com.insuranceai.form.catalog

## 목적

**생명보험** 청구 서류 접수 데이터 구조. 청구 유형별로 어떤 서류(=FormTemplate)가 필요한지와,
각 서류에 들어가는 정보(=FormField)를 코드로 정의하고 부팅 시 시드한다.

## 경로

- `back/insai/src/main/java/com/insuranceai/form/catalog/`
- API: `form/api/LifeInsuranceCatalogController.java`
- 테스트: `back/insai/src/test/java/com/insuranceai/form/catalog/`, `.../form/api/LifeInsuranceCatalogControllerTest.java`
- 저장: 런타임 시더(별도 마이그레이션 없음) → `form_templates` 테이블

## 핵심 타입

| 타입 | 역할 |
|---|---|
| `LifeInsuranceClaimType` | DEATH / DISABILITY / DIAGNOSIS / HOSPITALIZATION_SURGERY + 한글 label |
| `RequiredDocument` (record) | templateCode, name, condition(null=필수). `isMandatory()` |
| `LifeInsuranceDocumentCatalog` | `allTemplates()` (15종 FormTemplate) / `commonDocuments()` / `requiredDocuments(type)` / `requirementMatrix()` |
| `LifeInsuranceTemplateSeeder` | `ApplicationRunner`, `seed()` — code 기준 멱등 |
| `api.LifeInsuranceCatalogController` | `GET /catalog/life/claim-types`, `GET /catalog/life/claim-types/{type}/required-documents` |

## 서류 15종 (code = `life.*`)

공통: `claim-application`, `id-copy`, `bank-account-copy`, `power-of-attorney`(대리 시)
사망: `death-certificate`, `family-relation-certificate`, `accident-confirmation`(재해 시)
후유장해: `disability-diagnosis`, `medical-imaging-report`
진단: `diagnosis-certificate`, `pathology-report`(암 시)
입원·수술: `admission-discharge-confirmation`, `surgery-confirmation`(수술 시), `medical-expense-receipt`, `medical-expense-detail`(비급여 시)

각 서류의 칸 목록은 [features/life-insurance-claim-documents.md](../features/life-insurance-claim-documents.md).

## 불변식·규칙

- 모든 `allTemplates()` 항목은 `FormTemplate.create` 검증 통과 (필드 ≥1, key 중복 없음).
- `requirementMatrix()` 의 모든 `templateCode` 는 `allTemplates()` 에 존재해야 함 (테스트로 강제).
- 모든 청구 유형은 공통 서류 4종을 포함.
- 시더는 멱등: 이미 있는 code 는 건너뜀. 필드 변경은 `CreateTemplateService`(version+1) 로.

## 의존 관계

- 들어옴: 없음 (프론트가 REST 로 카탈로그 조회, 사정사가 서류 요청 시 templateCode → templateId 매핑).
- 나감: `form.domain` (FormTemplate/FormField).

## 테스트

`LifeInsuranceDocumentCatalogTest`(정합성 7), `LifeInsuranceTemplateSeederTest`(@DataJpaTest, 15건+멱등), `LifeInsuranceCatalogControllerTest`(@WebMvcTest 3).

## 주의 (gotchas)

- 시더는 `ApplicationRunner` → `@SpringBootTest` 통합 컨텍스트에서도 실행되어 공유 H2 에 `life.*` 15건 커밋. 개수 단정 테스트는 `deleteAll()` 선행.
- `@DataJpaTest` 는 ApplicationRunner 실행 안 함 → 시더 테스트는 직접 `new LifeInsuranceTemplateSeeder(repo).seed()`.
- 주민등록번호 필드는 **마스킹 저장 전제** (앞자리 등). 원문 저장 금지 정책 필요.
- `code → templateId` 조회 API 부재 → 프론트/사정사 UI 에서 code 기준 최신 템플릿을 찾으려면 `GET /templates?code=` 같은 엔드포인트 추가 필요.

## 상태·TODO

- [x] 15종 정의 + 매트릭스 + 시더 + 카탈로그 API, 로컬 구동 검증
- [ ] `GET /templates?code=life.death-certificate` (code→id 해석)
- [ ] 손해보험/제3보험 카탈로그 (범위 확대 시)
- [ ] RRN 마스킹 정책 적용
