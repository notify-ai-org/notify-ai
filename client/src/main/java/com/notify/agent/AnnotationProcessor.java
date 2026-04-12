package com.notify.agent;

import com.notify.agent.annotations.*;
import com.notify.agent.models.metadata.*;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Scans the client codebase for @EnableNotify and the configured base package,
 * then
 * records @Event, @Rule, @Callback, @Vocabulary, @Model, @VocabularySupplier,
 * and @SubjectSupplier with their mappings to fields, methods, or classes.
 */
public class AnnotationProcessor {

    private final String basePackage;
    private final List<EventMetadata> events = new ArrayList<>();
    private final List<RuleMetadata> rules = new ArrayList<>();
    private final List<CallbackMetadata> callbacks = new ArrayList<>();
    private final List<VocabularySupplierMetadata> vocabularySuppliers = new ArrayList<>();
    private final List<SubjectSupplierMetadata> subjectSuppliers = new ArrayList<>();
    private final List<ModelMetadata> models = new ArrayList<>();

    public AnnotationProcessor(String basePackage) {
        this.basePackage = basePackage == null || basePackage.isEmpty()
                ? "com.notify"
                : basePackage;
    }

    /**
     * Scan the base package and all sub-packages for annotations.
     */
    public void process() {
        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                        .forPackages(basePackage)
                        .setScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated, Scanners.FieldsAnnotated));

        // @Model classes and @Vocabulary fields
        Set<Class<?>> modelClasses = reflections.getTypesAnnotatedWith(Model.class);
        for (Class<?> modelClass : modelClasses) {
            processModel(modelClass);
        }

        // @Event, @Rule, @Callback, @VocabularySupplier, @SubjectSupplier on methods
        Set<Method> methods = reflections.getMethodsAnnotatedWith(Event.class);
        for (Method m : methods) {
            com.notify.agent.annotations.Event a = m.getAnnotation(com.notify.agent.annotations.Event.class);
            com.notify.agent.models.Event event = new com.notify.agent.models.Event();
            event.setName(a.key());
            event.setDescription(a.description());
            event.setEventType(a.eventType());
            event.setPreferredTimeWindow(a.preferredTimeWindow());
            event.setScheduleIntent(a.scheduleIntent());
            event.setPriority(a.priority());
            events.add(new EventMetadata(event, a.version(), m, m.getDeclaringClass()));
        }

        methods = reflections.getMethodsAnnotatedWith(Rule.class);
        for (Method m : methods) {
            Rule a = m.getAnnotation(Rule.class);
            rules.add(new RuleMetadata(a.name(), a.description(), a.event(), m, m.getDeclaringClass()));
        }

        methods = reflections.getMethodsAnnotatedWith(Callback.class);
        for (Method m : methods) {
            Callback a = m.getAnnotation(Callback.class);
            callbacks.add(new CallbackMetadata(a.event(), a.when(), m, m.getDeclaringClass()));
        }

        methods = reflections.getMethodsAnnotatedWith(VocabularySupplier.class);
        for (Method m : methods) {
            VocabularySupplier a = m.getAnnotation(VocabularySupplier.class);
            vocabularySuppliers
                    .add(new VocabularySupplierMetadata(a.event(), a.description(), m, m.getDeclaringClass()));
        }

        methods = reflections.getMethodsAnnotatedWith(SubjectSupplier.class);
        for (Method m : methods) {
            SubjectSupplier a = m.getAnnotation(SubjectSupplier.class);
            subjectSuppliers.add(new SubjectSupplierMetadata(a.event(), a.description(), m, m.getDeclaringClass()));
        }
    }

    private void processModel(Class<?> modelClass) {
        Model ann = modelClass.getAnnotation(Model.class);
        String description = ann != null ? ann.description() : "";

        List<VocabularyFieldMetadata> fields = new ArrayList<>();
        for (Field f : modelClass.getDeclaredFields()) {
            if (!f.isAnnotationPresent(Vocabulary.class))
                continue;
            Vocabulary v = f.getAnnotation(Vocabulary.class);
            String name = (v.name() == null || v.name().isEmpty()) ? f.getName() : v.name();
            fields.add(new VocabularyFieldMetadata(name, v.description(), f, modelClass));
        }
        models.add(new ModelMetadata(modelClass, description, fields));
    }

    public List<EventMetadata> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public List<RuleMetadata> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public List<CallbackMetadata> getCallbacks() {
        return Collections.unmodifiableList(callbacks);
    }

    public List<VocabularySupplierMetadata> getVocabularySuppliers() {
        return Collections.unmodifiableList(vocabularySuppliers);
    }

    public List<SubjectSupplierMetadata> getSubjectSuppliers() {
        return Collections.unmodifiableList(subjectSuppliers);
    }

    public List<ModelMetadata> getModels() {
        return Collections.unmodifiableList(models);
    }
}
