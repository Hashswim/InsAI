/**
 * LLM 오케스트레이션. 청구인 자유입력 → 템플릿 칸 구조화(/ai/structure),
 * 제출물 요약·하이라이트·누락 탐지(/ai/review-summary),
 * 이후: 사정 결과 평문 번역(/ai/translate-decision), 문의 1차 응답(/ai/answer).
 * LLM 제공자는 인터페이스 뒤로 숨겨 교체 가능하게 한다(기본: Claude).
 *
 * 하위 구조: api / application / domain / infrastructure
 * 향후 MSA 분리 대상: back/ai (8085)
 */
package com.insuranceai.ai;
