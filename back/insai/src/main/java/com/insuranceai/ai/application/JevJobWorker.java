package com.insuranceai.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceai.common.error.*;
import com.insuranceai.auth.domain.*;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class JevJobWorker {
    private final JevJobStore store;
    private final RunJevService runner;
    private final ObjectMapper mapper;
    private final AccountRepository accounts;
    public JevJobWorker(JevJobStore store, RunJevService runner, ObjectMapper mapper, AccountRepository accounts) {
        this.store=store; this.runner=runner; this.mapper=mapper;
        this.accounts=accounts;
    }
    public void tick() {
        store.expire(Instant.now());
        var next=store.claim();
        if (next.isEmpty()) return;
        var job=next.get();
        try {
            var account=accounts.findById(job.getRequestedBy()).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
            if (account.getStatus()!=AccountStatus.ACTIVE
                || (account.getRole()!=Role.CLAIMANT && account.getRole()!=Role.ADJUSTER))
                throw new BusinessException(ErrorCode.FORBIDDEN);
            var command=mapper.readValue(job.getCommandJson(),RunJevCommand.class);
            var result=runner.analyze(job.getRequestedBy(),command,job.getRequestId());
            store.complete(job.getId(),result);
        } catch (BusinessException e) { store.fail(job.getId(),e.getErrorCode()); }
        catch (Exception e) { store.fail(job.getId(),ErrorCode.INTERNAL_ERROR); }
    }
}
