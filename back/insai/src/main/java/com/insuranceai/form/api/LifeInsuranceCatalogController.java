package com.insuranceai.form.api;

import com.insuranceai.common.web.ApiResponse;
import com.insuranceai.form.catalog.LifeInsuranceClaimType;
import com.insuranceai.form.catalog.LifeInsuranceDocumentCatalog;
import com.insuranceai.form.catalog.RequiredDocument;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 생명보험 청구 유형별 요구 서류 안내. 손해사정사가 서류 요청 목록을 구성할 때 참고한다.
 */
@RestController
@RequestMapping("/catalog/life")
public class LifeInsuranceCatalogController {

	public record ClaimTypeView(String code, String label) {
	}

	@GetMapping("/claim-types")
	public ApiResponse<List<ClaimTypeView>> claimTypes() {
		return ApiResponse.ok(Arrays.stream(LifeInsuranceClaimType.values())
				.map(t -> new ClaimTypeView(t.name(), t.label()))
				.toList());
	}

	@GetMapping("/claim-types/{claimType}/required-documents")
	public ApiResponse<List<RequiredDocument>> requiredDocuments(@PathVariable LifeInsuranceClaimType claimType) {
		return ApiResponse.ok(LifeInsuranceDocumentCatalog.requiredDocuments(claimType));
	}
}
