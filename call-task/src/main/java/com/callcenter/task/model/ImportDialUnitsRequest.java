package com.callcenter.task.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class ImportDialUnitsRequest {

    private String sourceType = "JSON";

    @Valid
    @NotEmpty
    private List<ImportDialUnitItem> units;

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public List<ImportDialUnitItem> getUnits() {
        return units;
    }

    public void setUnits(List<ImportDialUnitItem> units) {
        this.units = units;
    }
}
