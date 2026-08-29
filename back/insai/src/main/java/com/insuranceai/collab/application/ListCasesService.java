package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.Case;
import com.insuranceai.collab.domain.CaseRepository;
import com.insuranceai.collab.domain.CaseStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ListCasesService {

	private final CaseRepository cases;

	public ListCasesService(CaseRepository cases) {
		this.cases = cases;
	}

	/** 내가 청구인이거나 배정 사정사인 케이스 (최신순). */
	@Transactional(readOnly = true)
	public List<CaseView> forUser(UUID userId) {
		Map<UUID, Case> merged = new LinkedHashMap<>();
		cases.findByClaimantId(userId).forEach(c -> merged.put(c.getId(), c));
		cases.findByAdjusterId(userId).forEach(c -> merged.put(c.getId(), c));
		return merged.values().stream()
				.sorted(Comparator.comparing(Case::getCreatedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.map(CaseView::from)
				.toList();
	}

	/** 아직 사정사가 배정되지 않은 케이스 (사정사가 배정받을 대상). */
	@Transactional(readOnly = true)
	public List<CaseView> unassigned() {
		return cases.findByStatus(CaseStatus.OPEN).stream()
				.filter(c -> c.getAdjusterId() == null)
				.map(CaseView::from)
				.toList();
	}
}
