-- policy 도메인: 청구인이 가입한 보험 계약(증권)
-- 정식 보험사 연동 전까지는 dev 프로파일에서 데모 시드로 채운다.

CREATE TABLE policies (
    id                  uuid                     NOT NULL,
    owner_id            uuid                     NOT NULL,
    insurer_name        varchar(60)              NOT NULL,
    product_name        varchar(120)             NOT NULL,
    policy_number       varchar(40)              NOT NULL,
    plan_type           varchar(20)              NOT NULL,
    insured_name        varchar(40)              NOT NULL,
    contract_date       date                     NOT NULL,
    coverage_start_date date                     NOT NULL,
    coverage_end_date   date,
    coverage_amount     bigint                   NOT NULL,
    monthly_premium     bigint                   NOT NULL,
    coverage_summary    varchar(500),
    status              varchar(20)              NOT NULL,
    created_at          timestamp(6) with time zone,
    updated_at          timestamp(6) with time zone,
    CONSTRAINT policies_pkey PRIMARY KEY (id),
    CONSTRAINT uk_policies_policy_number UNIQUE (policy_number)
);

CREATE INDEX idx_policies_owner_id ON policies (owner_id);
