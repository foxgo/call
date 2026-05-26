package com.callcenter.task.controller;

import com.callcenter.task.model.ImportBatchResponse;
import com.callcenter.task.service.CallTaskImportService;
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

@WebMvcTest(CallTaskImportController.class)
class CallTaskImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CallTaskImportService callTaskImportService;

    @Test
    void shouldCreateImportBatch() throws Exception {
        when(callTaskImportService.importDialUnits(eq(9L), eq(1001L), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/9/tasks/1001/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType": "JSON",
                                  "units": [
                                    {
                                      "phone": "13800138000"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldGetImportBatch() throws Exception {
        when(callTaskImportService.getImportBatch(9L, 1001L, 2001L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/9/tasks/1001/imports/2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importBatchId").value(2001));
    }

    private static ImportBatchResponse response() {
        return new ImportBatchResponse(2001L, 1001L, 9L, "JSON", "COMPLETED", 1, 1, 0, 0, null, null, null);
    }
}
