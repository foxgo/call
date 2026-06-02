package com.callcenter.task.controller;

import com.callcenter.task.service.DialResultWritebackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DialResultCallbackController.class)
class DialResultCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialResultWritebackService dialResultWritebackService;

    @Test
    void shouldAcceptDialResultCallback() throws Exception {
        mockMvc.perform(post("/api/v1/9/tasks/callbacks/dial-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": 1001,
                                  "dialUnitId": 11,
                                  "dispatchToken": "token-1",
                                  "resultStatus": "SUCCESS",
                                  "ringDurationSeconds": 8,
                                  "talkDurationSeconds": 45,
                                  "hangupCode": "NORMAL_CLEARING"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
