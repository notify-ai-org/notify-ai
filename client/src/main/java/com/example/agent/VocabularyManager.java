package com.example.agent;

import com.example.agent.sdk.dto.AttributeModelDto;
import com.example.agent.sdk.dto.ClassModelDto;
import com.example.agent.sdk.metadata.ModelMetadata;
import com.example.agent.sdk.metadata.VocabularyFieldMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts vocabulary using the AnnotationProcessor's @Model and @Vocabulary
 * metadata and forms ClassModelDto objects for acp-server vocabulary ingestion.
 */
public class VocabularyManager {

    private final AnnotationProcessor annotationProcessor;

    public VocabularyManager(AnnotationProcessor annotationProcessor) {
        this.annotationProcessor = annotationProcessor;
    }

    /**
     * Build ClassModelDto list from @Model classes and their @Vocabulary fields.
     */
    public List<ClassModelDto> toClassModelDtoList() {
        List<ClassModelDto> result = new ArrayList<>();
        for (ModelMetadata model : annotationProcessor.getModels()) {
            ClassModelDto dto = new ClassModelDto();
            Class<?> c = model.getModelClass();
            dto.setPackageName(c.getPackage() != null ? c.getPackage().getName() : "");
            dto.setClassName(c.getSimpleName());
            dto.setClassDescription(model.getDescription());
            dto.setClassType("CLASS");
            dto.setSuperClass(c.getSuperclass() != null && c.getSuperclass() != Object.class
                ? c.getSuperclass().getName() : null);
            dto.setInterfaces(java.util.Arrays.stream(c.getInterfaces()).map(Class::getName).collect(Collectors.toList()));

            List<AttributeModelDto> attrs = new ArrayList<>();
            for (VocabularyFieldMetadata vf : model.getVocabularyFields()) {
                AttributeModelDto a = new AttributeModelDto();
                a.setName(vf.getName());
                a.setType(vf.getField().getType().getSimpleName());
                a.setDescription(vf.getDescription());
                attrs.add(a);
            }
            dto.setAttributes(attrs);
            dto.setMethods(new ArrayList<>()); // optional; vocabulary consumer may not require
            result.add(dto);
        }
        return result;
    }
}
