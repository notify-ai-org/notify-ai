package com.example.agent.sdk.dto;

import java.util.List;

/**
 * DTO matching acp-server's ClassModel for vocabulary/class ingestion.
 * JSON shape must align with api.models.ClassModel for VocabularyConsumer.
 */
public class ClassModelDto {
    private String packageName;
    private String className;
    private String classDescription;
    private String classType; // CLASS, INTERFACE, ENUM, RECORD
    private String superClass;
    private List<String> interfaces;
    private List<AttributeModelDto> attributes;
    private List<MethodModelDto> methods;

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getClassDescription() { return classDescription; }
    public void setClassDescription(String classDescription) { this.classDescription = classDescription; }
    public String getClassType() { return classType; }
    public void setClassType(String classType) { this.classType = classType; }
    public String getSuperClass() { return superClass; }
    public void setSuperClass(String superClass) { this.superClass = superClass; }
    public List<String> getInterfaces() { return interfaces; }
    public void setInterfaces(List<String> interfaces) { this.interfaces = interfaces; }
    public List<AttributeModelDto> getAttributes() { return attributes; }
    public void setAttributes(List<AttributeModelDto> attributes) { this.attributes = attributes; }
    public List<MethodModelDto> getMethods() { return methods; }
    public void setMethods(List<MethodModelDto> methods) { this.methods = methods; }
}
