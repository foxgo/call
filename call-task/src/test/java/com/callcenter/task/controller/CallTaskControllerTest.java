package com.callcenter.task.controller;

import com.callcenter.task.model.TaskSummaryResponse;
import com.callcenter.task.service.CallTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CallTaskController.class)
class CallTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CallTaskService callTaskService;

    @Test
    void shouldCreateTask() throws Exception {
        when(callTaskService.createTask(eq(9L), any())).thenReturn(summary("INIT"));

        mockMvc.perform(post("/api/v1/9/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "task-a",
                                  "maxConcurrency": 8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INIT"))
                .andExpect(jsonPath("$.priority").value(4))
                .andExpect(jsonPath("$.nextDispatchTime").doesNotExist());
    }

    @Test
    void shouldStartPauseAndResumeTask() throws Exception {
        when(callTaskService.startTask(9L, 1001L)).thenReturn(summary("RUNNING"));
        when(callTaskService.pauseTask(9L, 1001L)).thenReturn(summary("PAUSED"));
        when(callTaskService.resumeTask(9L, 1001L)).thenReturn(summary("RUNNING"));
        when(callTaskService.getTask(9L, 1001L)).thenReturn(summary("RUNNING"));

        mockMvc.perform(post("/api/v1/9/tasks/1001/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/api/v1/9/tasks/1001/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        mockMvc.perform(post("/api/v1/9/tasks/1001/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(get("/api/v1/9/tasks/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(1001));
    }

    private static TaskSummaryResponse summary(String status) {
        return new TaskSummaryResponse(
                1001L,
                9L,
                "task-a",
                status,
                0,
                0,
                0,
                0,
                0,
                4,
                8,
                "HYBRID",
                "ANSWER",
                1D,
                0D,
                0D,
                0D,
                false,
                3600,
                200,
                null,
                null
        );
    }
}
