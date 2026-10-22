# 기능: 생명보험 청구 서류 데이터 구조

`com.insuranceai.form.catalog` 구현. 서류 15종 = `FormTemplate` 15개(code `life.*`), 부팅 시 시드.
각 서류의 "칸"은 `FormField(key, label, type, required, options, maxLength, min, max)`.

범례: **[*]** = 필수, `TEXT/DATE/NUMBER/SELECT`.

---

## 청구 유형 → 요구 서류 매트릭스

| 서류 | 사망 | 후유장해 | 진단 | 입원·수술 |
|---|:---:|:---:|:---:|:---:|
| 보험금 청구서 (`life.claim-application`) | ● | ● | ● | ● |
| 신분증 사본 (`life.id-copy`) | ● | ● | ● | ● |
| 통장 사본 (`life.bank-account-copy`) | ● | ● | ● | ● |
| 위임장 (`life.power-of-attorney`) | △대리 | △대리 | △대리 | △대리 |
| 사망진단서 (`life.death-certificate`) | ● | | | |
| 가족관계·기본증명서 (`life.family-relation-certificate`) | ● | | | |
| 사고사실확인원 (`life.accident-confirmation`) | △재해 | | | |
| 후유장해진단서 (`life.disability-diagnosis`) | | ● | | |
| 검사결과지 (`life.medical-imaging-report`) | | △ | | |
| 진단서 (`life.diagnosis-certificate`) | | | ● | |
| 조직검사결과지 (`life.pathology-report`) | | | △암 | |
| 입퇴원확인서 (`life.admission-discharge-confirmation`) | | | | ● |
| 수술확인서 (`life.surgery-confirmation`) | | | | △수술 |
| 진료비계산서·영수증 (`life.medical-expense-receipt`) | | | | ● |
| 진료비 세부내역서 (`life.medical-expense-detail`) | | | | △비급여 |

● 필수 · △ 조건부 (`RequiredDocument.condition` 에 설명 문자열)

---

## 서류별 칸 (FormField)

### 공통

**보험금 청구서** `life.claim-application`
`policyNumber`[*] 증권번호 · `claimReason`[*] SELECT(사망/후유장해/진단/입원/수술) · `insuredName`[*] 피보험자 성명 · `insuredRrn`[*] 피보험자 주민등록번호 · `claimantName`[*] 청구인 성명 · `claimantRrn`[*] 청구인 주민등록번호 · `claimantRelation`[*] SELECT(본인/배우자/자녀/부모/형제자매/상속인/기타) · `claimantPhone`[*] 연락처 · `eventDate`[*] DATE 사고·진단·사망일 · `accountBank`[*] 입금 은행 · `accountNumber`[*] 계좌번호 · `accountHolder`[*] 예금주 · `proxyClaim`[*] SELECT(아니오/예) 대리 청구 여부

**신분증 사본** `life.id-copy`
`idType`[*] SELECT(주민등록증/운전면허증/여권/장애인등록증) · `idHolderName`[*] 성명 · `idNumber`[*] 신분증 번호 · `issueDate` DATE 발급일자

**통장 사본** `life.bank-account-copy`
`bank`[*] 은행명 · `accountNumber`[*] 계좌번호 · `accountHolder`[*] 예금주 · `accountHolderRelation`[*] SELECT(청구인 본인/법정대리인/기타)

**위임장** `life.power-of-attorney` (대리 청구 시)
`principalName`[*] 위임인(청구권자) · `principalRrn`[*] · `agentName`[*] 수임인(대리인) · `agentRrn`[*] · `agentRelation`[*] 관계 · `scope`[*] 위임 범위 · `delegationDate`[*] DATE 위임일자

### 사망보험금

**사망진단서(시체검안서)** `life.death-certificate`
`deceasedName`[*] · `deceasedRrn`[*] · `deathDateTime`[*] 사망 연월일시 · `deathPlace`[*] 사망 장소 · `deathPlaceType`[*] SELECT(주택/의료기관/사회복지시설/산업장/도로/상업·서비스시설/공공시설/기타) · `deathManner`[*] SELECT(병사/외인사/기타 및 불상) — **사망의 종류** · `directCause`[*] 직접 사인 · `antecedentCause` (가) 중간선행사인 · `originatingCause` (나) 선행사인 · `deathCauseCode` 사인 분류기호(KCD) · `autopsyPerformed`[*] SELECT(아니오/예) 부검 여부 · `physicianName`[*] 진단·검안 의사 · `medicalInstitution`[*] 의료기관명 · `issueDate`[*] DATE

**가족관계·기본증명서** `life.family-relation-certificate`
`documentKind`[*] SELECT(가족관계증명서(상세)/기본증명서(상세)/제적등본/주민등록등본) · `basePersonName`[*] 기준인(피보험자) · `basePersonRrn`[*] · `heirs`[*] 상속인(수익자) 목록 · `issueDate`[*] DATE

**사고사실확인원** `life.accident-confirmation` (재해=외인사 시)
`accidentDateTime`[*] 사고 연월일시 · `accidentPlace`[*] · `accidentType`[*] SELECT(교통사고/추락·전도/익수/화재·화상/중독/질식/가해·폭행/기타) · `accidentDetail`[*] 사고 경위 · `reportingAgency`[*] 확인 기관 · `documentNumber` 사건·접수 번호

### 후유장해보험금

**후유장해진단서** `life.disability-diagnosis`
`patientName`[*] · `patientRrn`[*] · `injuryOrDiseaseName`[*] 상해명·병명 · `diseaseCode` KCD · `onsetDate`[*] DATE 발병·수상일 · `diagnosisDate`[*] DATE · `disabilitySite`[*] 장해 부위 · `disabilityCategory`[*] 장해분류표상 항목 · `disabilityRate`[*] NUMBER 0~100 지급률(%) · `permanence`[*] SELECT(영구장해/한시장해) · `temporaryPeriodMonths` NUMBER 0~600 한시장해 기간(개월) · `fixationDate` DATE 장해 고정일 · `physicianName`[*] · `medicalInstitution`[*]

**검사결과지** `life.medical-imaging-report`
`examType`[*] SELECT(X-ray/CT/MRI/초음파/근전도(EMG)/골밀도/기타) · `examDate`[*] DATE · `bodyPart`[*] 검사 부위 · `findings`[*] 판독 소견 · `readingPhysician` 판독 의사

### 진단보험금

**진단서** `life.diagnosis-certificate`
`patientName`[*] · `patientRrn`[*] · `diseaseName`[*] 병명 · `diseaseCode`[*] KCD 질병분류기호 · `onsetDate` DATE 발병일 · `diagnosisDate`[*] DATE 진단 확정일 · `diagnosisMethod`[*] SELECT(조직검사/혈액검사/영상검사/내시경/심전도·심초음파/임상적 진단/기타) · `treatmentPlan` 향후 치료 의견 · `admissionRequired` SELECT(아니오/예) · `physicianName`[*] · `medicalInstitution`[*] · `issueDate`[*] DATE

**조직검사결과지** `life.pathology-report` (암 확진 시)
`specimenCollectionDate`[*] DATE 검체 채취일 · `reportDate`[*] DATE 결과 보고일 · `specimenSite`[*] 검체 부위 · `histologicDiagnosis`[*] 조직학적 진단명 · `icdOCode` 형태 코드(ICD-O) · `behavior`[*] SELECT(양성/경계성 종양/제자리암(상피내암)/침윤암(악성)) — **행동양식** · `pathologistName` 판독 병리의

### 입원·수술급여금

**입퇴원확인서** `life.admission-discharge-confirmation`
`patientName`[*] · `patientRrn`[*] · `diseaseName`[*] 진단명 · `diseaseCode` KCD · `admissionDate`[*] DATE · `dischargeDate`[*] DATE · `admissionDays`[*] NUMBER 1~3650 총 입원일수 · `roomType` SELECT(일반병실/상급병실/중환자실/격리병실/낮병동) · `admissionRoute` SELECT(외래/응급실/전원) · `medicalInstitution`[*]

**수술확인서** `life.surgery-confirmation` (수술 시)
`patientName`[*] · `patientRrn`[*] · `surgeryName`[*] 수술명 · `surgeryDate`[*] DATE · `surgeryCode` 수술 분류·수가 코드 · `diseaseName`[*] 진단명 · `anesthesiaType` SELECT(전신마취/부위마취/국소마취/수면(진정)마취/마취 없음) · `operatorName`[*] 집도의 · `medicalInstitution`[*]

**진료비계산서·영수증** `life.medical-expense-receipt`
`patientName`[*] · `treatmentFrom`[*] DATE · `treatmentTo`[*] DATE · `totalAmount`[*] NUMBER 진료비 총액(원) · `patientPayment`[*] NUMBER 환자 부담 총액(원) · `coveredCopay` NUMBER 급여 본인부담금 · `nonCovered` NUMBER 비급여 금액 · `medicalInstitution`[*] · `receiptNumber` 영수증 번호

**진료비 세부내역서** `life.medical-expense-detail` (비급여 확인 시)
`patientName`[*] · `treatmentFrom`[*] DATE · `treatmentTo`[*] DATE · `itemizedNonCovered` 비급여 항목별 내역 · `medicalInstitution`[*]

---

## 주의

- **주민등록번호(RRN) 필드는 마스킹 저장 전제.** 원문 저장 금지 정책·검증을 추가할 것.
- 날짜는 `YYYY-MM-DD` (FormField DATE 검증). "사망 연월일시"처럼 시각 포함은 TEXT.
- `disabilityRate` 는 장해분류표 지급률(%) — 보험금 산정의 핵심 수치.
- `behavior`(행동양식)·`histologicDiagnosis` 는 암 보험금 지급 판단의 핵심 (제자리암 vs 침윤암 구분).
- 카탈로그 확장(손해보험 등) 시 code 프리픽스 분리(`life.` / `general.` …).
