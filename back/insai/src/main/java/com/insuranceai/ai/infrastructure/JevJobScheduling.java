package com.insuranceai.ai.infrastructure;

import com.insuranceai.ai.application.JevJobWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Opt-in until deployment explicitly provisions worker capacity. Jobs remain durable while off. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="insai.jev.jobs.worker-enabled", havingValue="true")
public class JevJobScheduling {
    private final JevJobWorker worker;
    public JevJobScheduling(JevJobWorker worker) { this.worker=worker; }
    @Scheduled(fixedDelay=1000)
    public void dispatch() { worker.tick(); }
}
