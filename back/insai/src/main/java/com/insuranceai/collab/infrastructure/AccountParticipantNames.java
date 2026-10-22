package com.insuranceai.collab.infrastructure;

import com.insuranceai.auth.domain.Account;
import com.insuranceai.auth.domain.AccountRepository;
import com.insuranceai.collab.application.ParticipantNames;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * collab → auth 경계 어댑터. 참여자 표시 이름을 계정에서 가져온다.
 */
@Component
public class AccountParticipantNames implements ParticipantNames {

	private final AccountRepository accounts;

	public AccountParticipantNames(AccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	public String nameOf(UUID accountId) {
		if (accountId == null) {
			return null;
		}
		return accounts.findById(accountId).map(Account::getName).orElse(null);
	}
}
