package com.example.agent.sdk.dto;

import java.util.List;

public class MethodModelDto {
    private String name;
    private String returnType;
    private String description;
    private List<ParameterModelDto> parameters;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<ParameterModelDto> getParameters() { return parameters; }
    public void setParameters(List<ParameterModelDto> parameters) { this.parameters = parameters; }
}
