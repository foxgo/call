package com.callcenter.ingestion.domain.analysis;

import java.util.List;

public record CallAnalysisResponse(
        List<String> tags,
        Boolean riskFlag,
        Float qualityScore,
        String aiVersion
) {
}
