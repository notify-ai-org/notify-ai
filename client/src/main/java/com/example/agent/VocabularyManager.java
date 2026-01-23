package com.example.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.example.agent.models.AttributeModel;
import com.example.agent.models.ClassModel;
import com.example.agent.models.metadata.ModelMetadata;
import com.example.agent.models.metadata.VocabularyFieldMetadata;

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
    public List<ClassModel> toClassModelDtoList() {
        List<ClassModel> result = new ArrayList<>();
        for (ModelMetadata model : annotationProcessor.getModels()) {
            ClassModel dto = new ClassModel();
            Class<?> c = model.getModelClass();
            dto.setPackageName(c.getPackage() != null ? c.getPackage().getName() : "");
            dto.setClassName(c.getSimpleName());
            dto.setClassDescription(model.getDescription());
            //dto.setClassType("CLASS");
            dto.setSuperClass(c.getSuperclass() != null && c.getSuperclass() != Object.class
                ? c.getSuperclass().getName() : null);
            dto.setInterfaces(java.util.Arrays.stream(c.getInterfaces()).map(Class::getName).collect(Collectors.toList()));

            List<AttributeModel> attrs = new ArrayList<>();
            for (VocabularyFieldMetadata vf : model.getVocabularyFields()) {
                AttributeModel a = new AttributeModel();
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
