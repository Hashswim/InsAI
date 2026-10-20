package com.insuranceai.ai.api;

import com.insuranceai.ai.application.RunJevService;
import com.insuranceai.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(JevController.class)
@Import(SecurityConfig.class)
class JevControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RunJevService service;
    @Test void rejectsMissingDocuments() throws Exception {
        mvc.perform(post("/ai/jev").header("X-User-Id","11111111-1111-1111-1111-111111111111")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"caseId\":\"22222222-2222-2222-2222-222222222222\",\"documents\":[]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
