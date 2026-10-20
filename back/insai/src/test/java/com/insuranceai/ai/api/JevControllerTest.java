package com.insuranceai.ai.api;

import com.insuranceai.ai.application.RunJevService;
import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import com.insuranceai.ai.infrastructure.JevSecurityConfig;
import com.insuranceai.auth.application.AuthenticateAccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import java.util.UUID;
import com.insuranceai.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.*;

@WebMvcTest(JevController.class)
@Import({SecurityConfig.class, JevSecurityConfig.class})
class JevControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RunJevService service;
    @MockitoBean AuthenticateAccessTokenService authentication;
    @BeforeEach void authenticateFixture() {
        when(authentication.authenticate("test-jwt")).thenReturn(new AuthenticateAccessTokenService.Actor(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "ROLE_CLAIMANT"));
    }
    @Test void rejectsMissingDocuments() throws Exception {
        mvc.perform(post("/ai/jev").header("Authorization","Bearer test-jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"caseId\":\"22222222-2222-2222-2222-222222222222\",\"documents\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(header().exists("X-Request-ID"));
        verifyNoInteractions(service);
    }

    @Test void invalidRequestIdIsRejectedWithoutEcho() throws Exception {
        var result=mvc.perform(post("/ai/jev").header("X-Request-ID","SENTINEL_PATIENT"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_ID"))
            .andReturn();
        UUID.fromString(result.getResponse().getHeader("X-Request-ID"));
        org.junit.jupiter.api.Assertions.assertFalse(result.getResponse().getContentAsString().contains("SENTINEL"));
        verifyNoInteractions(service);
    }

    @Test void publicErrorPreservesRequestIdAndStandardEnvelope() throws Exception {
        UUID requestId=UUID.randomUUID();
        when(service.run(any(),any(),eq(requestId))).thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));
        mvc.perform(post("/ai/jev").header("X-Request-ID",requestId.toString())
            .header("Authorization","Bearer test-jwt")
            .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
            .andExpect(status().isGatewayTimeout())
            .andExpect(header().string("X-Request-ID",requestId.toString()))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"));
    }

    private String validRequest() {
        return """
            {"caseId":"22222222-2222-2222-2222-222222222222","documents":[
            {"fileId":"33333333-3333-3333-3333-333333333333","kind":"DIAGNOSIS"},
            {"fileId":"44444444-4444-4444-4444-444444444444","kind":"TREATMENT_CONFIRMATION"},
            {"fileId":"55555555-5555-5555-5555-555555555555","kind":"ITEMIZED_BILL"}]}
            """;
    }

    @Test void unsupportedMediaHasStandardErrorAndRequestId() throws Exception {
        mvc.perform(post("/ai/jev").header("Authorization","Bearer test-jwt")
            .contentType(MediaType.TEXT_PLAIN).content("SENTINEL_PATIENT"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(header().exists("X-Request-ID"))
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(service);
    }

    @Test void unsupportedMethodHasStandardError() throws Exception {
        mvc.perform(put("/ai/jev").header("Authorization","Bearer test-jwt"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().exists("X-Request-ID"))
            .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
        verifyNoInteractions(service);
    }
}
