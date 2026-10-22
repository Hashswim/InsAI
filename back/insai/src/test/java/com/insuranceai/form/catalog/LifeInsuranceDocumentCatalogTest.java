package com.insuranceai.form.catalog;

import com.insuranceai.form.domain.FormField;
import com.insuranceai.form.domain.FormTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LifeInsuranceDocumentCatalogTest {

	private final List<FormTemplate> templates = LifeInsuranceDocumentCatalog.allTemplates();

	@Test
	@DisplayName("모든 템플릿은 유효하게 생성되고 code 는 고유하다")
	void templatesAreValidAndUnique() {
		assertThat(templates).hasSize(15);

		Set<String> codes = new HashSet<>();
		for (FormTemplate t : templates) {
			assertThat(t.getCode()).startsWith("life.");
			assertThat(t.getFields()).isNotEmpty();
			assertThat(codes.add(t.getCode()))
					.as("중복 code: %s", t.getCode())
					.isTrue();
		}
	}

	@Test
	@DisplayName("템플릿 내부 필드 key 는 중복이 없다")
	void fieldKeysUniqueWithinTemplate() {
		for (FormTemplate t : templates) {
			Set<String> keys = new HashSet<>();
			for (FormField f : t.getFields()) {
				assertThat(keys.add(f.key()))
						.as("%s 템플릿에 중복 필드 key: %s", t.getCode(), f.key())
						.isTrue();
			}
		}
	}

	@Test
	@DisplayName("요구 서류 매트릭스는 카탈로그에 존재하는 code 만 참조한다")
	void requirementMatrixReferencesKnownCodes() {
		Set<String> known = new HashSet<>();
		templates.forEach(t -> known.add(t.getCode()));

		for (List<RequiredDocument> docs : LifeInsuranceDocumentCatalog.requirementMatrix().values()) {
			for (RequiredDocument doc : docs) {
				assertThat(known)
						.as("알 수 없는 templateCode: %s", doc.templateCode())
						.contains(doc.templateCode());
			}
		}
	}

	@Test
	@DisplayName("모든 청구 유형은 공통 서류 4종을 포함한다")
	void everyClaimTypeIncludesCommonDocuments() {
		List<String> commonCodes = LifeInsuranceDocumentCatalog.commonDocuments().stream()
				.map(RequiredDocument::templateCode)
				.toList();
		assertThat(commonCodes).hasSize(4);

		for (LifeInsuranceClaimType type : LifeInsuranceClaimType.values()) {
			List<String> codes = LifeInsuranceDocumentCatalog.requiredDocuments(type).stream()
					.map(RequiredDocument::templateCode)
					.toList();
			assertThat(codes).containsAll(commonCodes);
		}
	}

	@Test
	@DisplayName("사망 청구는 사망진단서·가족관계증명서를 필수로 요구한다")
	void deathClaimRequiresDeathCertificate() {
		List<RequiredDocument> docs = LifeInsuranceDocumentCatalog.requiredDocuments(LifeInsuranceClaimType.DEATH);

		assertThat(docs).anyMatch(d -> d.templateCode().equals("life.death-certificate") && d.isMandatory());
		assertThat(docs).anyMatch(d -> d.templateCode().equals("life.family-relation-certificate") && d.isMandatory());
		assertThat(docs).anyMatch(d -> d.templateCode().equals("life.accident-confirmation") && !d.isMandatory());
	}

	@Test
	@DisplayName("진단 청구는 진단서를 필수로, 조직검사결과지를 조건부로 요구한다")
	void diagnosisClaimRequirements() {
		List<RequiredDocument> docs = LifeInsuranceDocumentCatalog.requiredDocuments(LifeInsuranceClaimType.DIAGNOSIS);

		assertThat(docs).anyMatch(d -> d.templateCode().equals("life.diagnosis-certificate") && d.isMandatory());
		assertThat(docs).anyMatch(d -> d.templateCode().equals("life.pathology-report") && d.condition() != null);
	}
}
