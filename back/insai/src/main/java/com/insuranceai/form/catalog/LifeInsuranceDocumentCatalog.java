package com.insuranceai.form.catalog;

import com.insuranceai.form.domain.FieldType;
import com.insuranceai.form.domain.FormField;
import com.insuranceai.form.domain.FormTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 생명보험 청구 서류 접수 카탈로그.
 *
 * <p>각 서류 = 하나의 {@link FormTemplate}(code + 필드 목록).
 * 청구 유형별로 어떤 서류가 필요한지는 {@link #requiredDocuments(LifeInsuranceClaimType)} 에 정의한다.
 *
 * <p>KCD = 한국표준질병사인분류 코드, RRN = 주민등록번호(앞 6자리 등 마스킹 저장 전제).
 */
public final class LifeInsuranceDocumentCatalog {

	private LifeInsuranceDocumentCatalog() {
	}

	// ---------------------------------------------------------------------
	// 서류(템플릿) 정의
	// ---------------------------------------------------------------------

	/** 모든 생명보험 청구 서류 템플릿(공통 + 유형별). 순서는 접수 화면 노출 순서. */
	public static List<FormTemplate> allTemplates() {
		return List.of(
				claimApplication(),
				idCopy(),
				bankAccountCopy(),
				powerOfAttorney(),
				deathCertificate(),
				familyRelationCertificate(),
				accidentConfirmation(),
				disabilityDiagnosis(),
				medicalImagingReport(),
				diagnosisCertificate(),
				pathologyReport(),
				admissionDischargeConfirmation(),
				surgeryConfirmation(),
				medicalExpenseReceipt(),
				medicalExpenseDetail()
		);
	}

	// --- 공통 서류 ---

	/** 보험금 청구서 */
	public static FormTemplate claimApplication() {
		return template("life.claim-application", "보험금 청구서", List.of(
				text("policyNumber", "증권번호", true),
				select("claimReason", "청구 사유", true, "사망", "후유장해", "진단", "입원", "수술"),
				text("insuredName", "피보험자 성명", true),
				text("insuredRrn", "피보험자 주민등록번호", true),
				text("claimantName", "청구인 성명", true),
				text("claimantRrn", "청구인 주민등록번호", true),
				select("claimantRelation", "피보험자와의 관계", true,
						"본인", "배우자", "자녀", "부모", "형제자매", "상속인", "기타"),
				text("claimantPhone", "청구인 연락처", true),
				date("eventDate", "사고·진단·사망일", true),
				text("accountBank", "보험금 입금 은행", true),
				text("accountNumber", "계좌번호", true),
				text("accountHolder", "예금주", true),
				select("proxyClaim", "대리 청구 여부", true, "아니오", "예")
		));
	}

	/** 청구인 신분증 사본 */
	public static FormTemplate idCopy() {
		return template("life.id-copy", "신분증 사본", List.of(
				select("idType", "신분증 종류", true, "주민등록증", "운전면허증", "여권", "장애인등록증"),
				text("idHolderName", "성명", true),
				text("idNumber", "신분증 번호", true),
				date("issueDate", "발급일자", false)
		));
	}

	/** 보험금 수령 통장 사본 */
	public static FormTemplate bankAccountCopy() {
		return template("life.bank-account-copy", "보험금 수령 통장 사본", List.of(
				text("bank", "은행명", true),
				text("accountNumber", "계좌번호", true),
				text("accountHolder", "예금주", true),
				select("accountHolderRelation", "예금주-청구인 관계", true, "청구인 본인", "법정대리인", "기타")
		));
	}

	/** 위임장 (대리 청구 시) */
	public static FormTemplate powerOfAttorney() {
		return template("life.power-of-attorney", "위임장", List.of(
				text("principalName", "위임인(청구권자) 성명", true),
				text("principalRrn", "위임인 주민등록번호", true),
				text("agentName", "수임인(대리인) 성명", true),
				text("agentRrn", "수임인 주민등록번호", true),
				text("agentRelation", "수임인과의 관계", true),
				text("scope", "위임 범위", true),
				date("delegationDate", "위임일자", true)
		));
	}

	// --- 사망보험금 ---

	/** 사망진단서(시체검안서) */
	public static FormTemplate deathCertificate() {
		return template("life.death-certificate", "사망진단서(시체검안서)", List.of(
				text("deceasedName", "사망자 성명", true),
				text("deceasedRrn", "사망자 주민등록번호", true),
				text("deathDateTime", "사망 연월일시", true),
				text("deathPlace", "사망 장소", true),
				select("deathPlaceType", "사망 장소 구분", true,
						"주택", "의료기관", "사회복지시설", "산업장", "도로", "상업·서비스시설", "공공시설", "기타"),
				select("deathManner", "사망의 종류", true, "병사", "외인사", "기타 및 불상"),
				text("directCause", "직접 사인", true),
				text("antecedentCause", "(가) 중간선행사인", false),
				text("originatingCause", "(나) 선행사인", false),
				text("deathCauseCode", "사인 분류기호(KCD)", false),
				select("autopsyPerformed", "부검 실시 여부", true, "아니오", "예"),
				text("physicianName", "진단·검안 의사", true),
				text("medicalInstitution", "의료기관명", true),
				date("issueDate", "발급일자", true)
		));
	}

	/** 가족관계증명서 / 기본증명서 */
	public static FormTemplate familyRelationCertificate() {
		return template("life.family-relation-certificate", "가족관계·기본증명서", List.of(
				select("documentKind", "서류 종류", true,
						"가족관계증명서(상세)", "기본증명서(상세)", "제적등본", "주민등록등본"),
				text("basePersonName", "기준인(피보험자) 성명", true),
				text("basePersonRrn", "기준인 주민등록번호", true),
				text("heirs", "상속인(수익자) 목록", true),
				date("issueDate", "발급일자", true)
		));
	}

	/** 사고사실확인원 (재해사망 시) */
	public static FormTemplate accidentConfirmation() {
		return template("life.accident-confirmation", "사고사실확인원", List.of(
				text("accidentDateTime", "사고 연월일시", true),
				text("accidentPlace", "사고 장소", true),
				select("accidentType", "사고 종류", true,
						"교통사고", "추락·전도", "익수", "화재·화상", "중독", "질식", "가해·폭행", "기타"),
				text("accidentDetail", "사고 경위", true),
				text("reportingAgency", "확인 기관", true),
				text("documentNumber", "사건·접수 번호", false)
		));
	}

	// --- 후유장해보험금 ---

	/** 후유장해진단서 */
	public static FormTemplate disabilityDiagnosis() {
		return template("life.disability-diagnosis", "후유장해진단서", List.of(
				text("patientName", "환자 성명", true),
				text("patientRrn", "환자 주민등록번호", true),
				text("injuryOrDiseaseName", "상해명·병명", true),
				text("diseaseCode", "질병분류기호(KCD)", false),
				date("onsetDate", "발병·수상일", true),
				date("diagnosisDate", "진단일", true),
				text("disabilitySite", "장해 부위", true),
				text("disabilityCategory", "장해분류표상 항목", true),
				number("disabilityRate", "장해 지급률(%)", true, 0.0, 100.0),
				select("permanence", "장해 유형", true, "영구장해", "한시장해"),
				number("temporaryPeriodMonths", "한시장해 예상 기간(개월)", false, 0.0, 600.0),
				date("fixationDate", "장해 고정일", false),
				text("physicianName", "진단 의사", true),
				text("medicalInstitution", "의료기관명", true)
		));
	}

	/** 검사결과지 (영상·기능검사) */
	public static FormTemplate medicalImagingReport() {
		return template("life.medical-imaging-report", "검사결과지", List.of(
				select("examType", "검사 종류", true,
						"X-ray", "CT", "MRI", "초음파", "근전도(EMG)", "골밀도", "기타"),
				date("examDate", "검사일", true),
				text("bodyPart", "검사 부위", true),
				text("findings", "판독 소견", true),
				text("readingPhysician", "판독 의사", false)
		));
	}

	// --- 진단보험금 ---

	/** 진단서 */
	public static FormTemplate diagnosisCertificate() {
		return template("life.diagnosis-certificate", "진단서", List.of(
				text("patientName", "환자 성명", true),
				text("patientRrn", "환자 주민등록번호", true),
				text("diseaseName", "병명", true),
				text("diseaseCode", "질병분류기호(KCD)", true),
				date("onsetDate", "발병일", false),
				date("diagnosisDate", "진단 확정일", true),
				select("diagnosisMethod", "진단 방법", true,
						"조직검사", "혈액검사", "영상검사", "내시경", "심전도·심초음파", "임상적 진단", "기타"),
				text("treatmentPlan", "향후 치료 의견", false),
				select("admissionRequired", "입원 필요 여부", false, "아니오", "예"),
				text("physicianName", "진단 의사", true),
				text("medicalInstitution", "의료기관명", true),
				date("issueDate", "발급일자", true)
		));
	}

	/** 조직검사결과지 (암 진단 시) */
	public static FormTemplate pathologyReport() {
		return template("life.pathology-report", "조직검사결과지", List.of(
				date("specimenCollectionDate", "검체 채취일", true),
				date("reportDate", "결과 보고일", true),
				text("specimenSite", "검체 부위", true),
				text("histologicDiagnosis", "조직학적 진단명", true),
				text("icdOCode", "형태 코드(ICD-O)", false),
				select("behavior", "행동양식", true,
						"양성", "경계성 종양", "제자리암(상피내암)", "침윤암(악성)"),
				text("pathologistName", "판독 병리의", false)
		));
	}

	// --- 입원·수술급여금 ---

	/** 입퇴원확인서 */
	public static FormTemplate admissionDischargeConfirmation() {
		return template("life.admission-discharge-confirmation", "입퇴원확인서", List.of(
				text("patientName", "환자 성명", true),
				text("patientRrn", "환자 주민등록번호", true),
				text("diseaseName", "진단명", true),
				text("diseaseCode", "질병분류기호(KCD)", false),
				date("admissionDate", "입원일", true),
				date("dischargeDate", "퇴원일", true),
				number("admissionDays", "총 입원일수", true, 1.0, 3650.0),
				select("roomType", "병실 구분", false,
						"일반병실", "상급병실", "중환자실", "격리병실", "낮병동"),
				select("admissionRoute", "입원 경로", false, "외래", "응급실", "전원"),
				text("medicalInstitution", "의료기관명", true)
		));
	}

	/** 수술확인서 */
	public static FormTemplate surgeryConfirmation() {
		return template("life.surgery-confirmation", "수술확인서", List.of(
				text("patientName", "환자 성명", true),
				text("patientRrn", "환자 주민등록번호", true),
				text("surgeryName", "수술명", true),
				date("surgeryDate", "수술일", true),
				text("surgeryCode", "수술 분류·수가 코드", false),
				text("diseaseName", "진단명", true),
				select("anesthesiaType", "마취 종류", false,
						"전신마취", "부위마취", "국소마취", "수면(진정)마취", "마취 없음"),
				text("operatorName", "집도의", true),
				text("medicalInstitution", "의료기관명", true)
		));
	}

	/** 진료비계산서·영수증 */
	public static FormTemplate medicalExpenseReceipt() {
		return template("life.medical-expense-receipt", "진료비계산서·영수증", List.of(
				text("patientName", "환자 성명", true),
				date("treatmentFrom", "진료 기간(시작)", true),
				date("treatmentTo", "진료 기간(종료)", true),
				number("totalAmount", "진료비 총액(원)", true, 0.0, 1_000_000_000.0),
				number("patientPayment", "환자 부담 총액(원)", true, 0.0, 1_000_000_000.0),
				number("coveredCopay", "급여 본인부담금(원)", false, 0.0, 1_000_000_000.0),
				number("nonCovered", "비급여 금액(원)", false, 0.0, 1_000_000_000.0),
				text("medicalInstitution", "의료기관명", true),
				text("receiptNumber", "영수증 번호", false)
		));
	}

	/** 진료비 세부내역서 */
	public static FormTemplate medicalExpenseDetail() {
		return template("life.medical-expense-detail", "진료비 세부내역서", List.of(
				text("patientName", "환자 성명", true),
				date("treatmentFrom", "진료 기간(시작)", true),
				date("treatmentTo", "진료 기간(종료)", true),
				text("itemizedNonCovered", "비급여 항목별 내역", false),
				text("medicalInstitution", "의료기관명", true)
		));
	}

	// ---------------------------------------------------------------------
	// 청구 유형별 요구 서류
	// ---------------------------------------------------------------------

	/** 모든 청구 유형에 공통으로 요구되는 서류. */
	public static List<RequiredDocument> commonDocuments() {
		return List.of(
				RequiredDocument.required("life.claim-application", "보험금 청구서"),
				RequiredDocument.required("life.id-copy", "청구인 신분증 사본"),
				RequiredDocument.required("life.bank-account-copy", "보험금 수령 통장 사본"),
				RequiredDocument.conditional("life.power-of-attorney", "위임장", "대리인이 청구하는 경우")
		);
	}

	/** 공통 서류 + 청구 유형별 추가 서류. */
	public static List<RequiredDocument> requiredDocuments(LifeInsuranceClaimType type) {
		return switch (type) {
			case DEATH -> concat(commonDocuments(), List.of(
					RequiredDocument.required("life.death-certificate", "사망진단서(시체검안서)"),
					RequiredDocument.required("life.family-relation-certificate", "가족관계·기본증명서"),
					RequiredDocument.conditional("life.accident-confirmation", "사고사실확인원",
							"재해(외인사)로 사망한 경우")
			));
			case DISABILITY -> concat(commonDocuments(), List.of(
					RequiredDocument.required("life.disability-diagnosis", "후유장해진단서"),
					RequiredDocument.conditional("life.medical-imaging-report", "검사결과지",
							"장해 상태 확인을 위한 영상·기능검사가 있는 경우")
			));
			case DIAGNOSIS -> concat(commonDocuments(), List.of(
					RequiredDocument.required("life.diagnosis-certificate", "진단서"),
					RequiredDocument.conditional("life.pathology-report", "조직검사결과지",
							"암 진단으로 조직학적 확진이 필요한 경우")
			));
			case HOSPITALIZATION_SURGERY -> concat(commonDocuments(), List.of(
					RequiredDocument.required("life.admission-discharge-confirmation", "입퇴원확인서"),
					RequiredDocument.conditional("life.surgery-confirmation", "수술확인서",
							"수술을 받은 경우"),
					RequiredDocument.required("life.medical-expense-receipt", "진료비계산서·영수증"),
					RequiredDocument.conditional("life.medical-expense-detail", "진료비 세부내역서",
							"비급여 내역 확인이 필요한 경우")
			));
		};
	}

	/** 전체 유형의 요구 서류 매트릭스. */
	public static Map<LifeInsuranceClaimType, List<RequiredDocument>> requirementMatrix() {
		Map<LifeInsuranceClaimType, List<RequiredDocument>> matrix = new LinkedHashMap<>();
		for (LifeInsuranceClaimType type : LifeInsuranceClaimType.values()) {
			matrix.put(type, requiredDocuments(type));
		}
		return matrix;
	}

	// ---------------------------------------------------------------------
	// 내부 헬퍼
	// ---------------------------------------------------------------------

	private static FormTemplate template(String code, String name, List<FormField> fields) {
		return FormTemplate.create(code, name, fields, 1);
	}

	private static FormField text(String key, String label, boolean required) {
		return new FormField(key, label, FieldType.TEXT, required, null, null, null, null);
	}

	private static FormField date(String key, String label, boolean required) {
		return new FormField(key, label, FieldType.DATE, required, null, null, null, null);
	}

	private static FormField number(String key, String label, boolean required, Double min, Double max) {
		return new FormField(key, label, FieldType.NUMBER, required, null, null, min, max);
	}

	private static FormField select(String key, String label, boolean required, String... options) {
		return new FormField(key, label, FieldType.SELECT, required, List.of(options), null, null, null);
	}

	@SafeVarargs
	private static <T> List<T> concat(List<T>... lists) {
		return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
	}
}
