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
 * <p>각 서류의 입력 칸은 첨부 파일과 선택 문의 사항뿐이다. 서류 내용은 AI 가 파일에서 추출한다.
 */
public final class LifeInsuranceDocumentCatalog {

	private LifeInsuranceDocumentCatalog() {
	}

	// ---------------------------------------------------------------------
	// 서류(템플릿) 정의
	// ---------------------------------------------------------------------

	/** 서류 code → 서류명. 순서는 접수 화면 노출 순서. */
	private static final Map<String, String> DOCUMENTS = linked(
			"life.claim-application", "보험금 청구서",
			"life.id-copy", "신분증 사본",
			"life.bank-account-copy", "보험금 수령 통장 사본",
			"life.power-of-attorney", "위임장",
			"life.death-certificate", "사망진단서(시체검안서)",
			"life.family-relation-certificate", "가족관계·기본증명서",
			"life.accident-confirmation", "사고사실확인원",
			"life.disability-diagnosis", "후유장해진단서",
			"life.medical-imaging-report", "검사결과지",
			"life.diagnosis-certificate", "진단서",
			"life.pathology-report", "조직검사결과지",
			"life.admission-discharge-confirmation", "입퇴원확인서",
			"life.surgery-confirmation", "수술확인서",
			"life.medical-expense-receipt", "진료비계산서·영수증",
			"life.medical-expense-detail", "진료비 세부내역서"
	);

	/**
	 * 모든 생명보험 청구 서류 템플릿. 청구인은 서류 파일과 (선택) 문의 사항만 적고,
	 * 질병코드·날짜·금액 같은 내용은 AI(JEV)가 파일에서 읽는다.
	 */
	public static List<FormTemplate> allTemplates() {
		return DOCUMENTS.entrySet().stream()
				.map(d -> FormTemplate.create(d.getKey(), d.getValue(), List.of(attachment(d.getValue()), INQUIRY), 1))
				.toList();
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

	private static final FormField INQUIRY =
			new FormField("inquiry", "추가 문의 사항", FieldType.TEXT, false, null, 1000, null, null);

	private static FormField attachment(String name) {
		return new FormField("file", name + " 파일", FieldType.FILE, true, null, null, null, null);
	}

	private static Map<String, String> linked(String... pairs) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return map;
	}

	@SafeVarargs
	private static <T> List<T> concat(List<T>... lists) {
		return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
	}
}
