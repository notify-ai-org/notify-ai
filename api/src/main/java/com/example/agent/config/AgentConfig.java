package com.example.agent.config;

import java.util.List;

public class AgentConfig {
    private String id;
    private String name;
    private String description;
    private String resourcePath;
    private String inputSchemaTitle;
    private String inputSchemaDescription;
    private String inputClass;
    private String outputSchemaTitle;
    private String outputSchemaDescription;
    private String outputClass;
    private String outputType;
    private List<String> tools;
    private String outputKey;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getInputSchemaTitle() {
        return inputSchemaTitle;
    }

    public void setInputSchemaTitle(String inputSchemaTitle) {
        this.inputSchemaTitle = inputSchemaTitle;
    }

    public String getInputSchemaDescription() {
        return inputSchemaDescription;
    }

    public void setInputSchemaDescription(String inputSchemaDescription) {
        this.inputSchemaDescription = inputSchemaDescription;
    }

    public String getInputClass() {
        return inputClass;
    }

    public void setInputClass(String inputClass) {
        this.inputClass = inputClass;
    }

    public String getOutputSchemaTitle() {
        return outputSchemaTitle;
    }

    public void setOutputSchemaTitle(String outputSchemaTitle) {
        this.outputSchemaTitle = outputSchemaTitle;
    }

    public String getOutputSchemaDescription() {
        return outputSchemaDescription;
    }

    public void setOutputSchemaDescription(String outputSchemaDescription) {
        this.outputSchemaDescription = outputSchemaDescription;
    }

    public String getOutputClass() {
        return outputClass;
    }

    public void setOutputClass(String outputClass) {
        this.outputClass = outputClass;
    }

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public List<String> getTools() {
        return tools;
    }

    public void setTools(List<String> tools) {
        this.tools = tools;
    }

    public String getOutputKey() {
        return outputKey;
    }

    public void setOutputKey(String outputKey) {
        this.outputKey = outputKey;
    }
}
