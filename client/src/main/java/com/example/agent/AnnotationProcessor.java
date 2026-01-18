package com.example.sdk.core;

import com.example.notification.annotations.*;
import com.example.sdk.model.*;
import org.reflections.Reflections;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Annotation Processor that scans SDK domain classes and builds
 * structured metadata models to be sent to the Agent Server.
 */
public class AnnotationProcessor {

    private final String basePackage;
    private final List<ClassModel> classModels = new ArrayList<>();
    private final Map<String, CallbackRegistry> callbacks = new HashMap<>();
    private final Map<String, RuleRegistry> rules = new HashMap<>();
    private final Map<String, EventRegistry> events = new HashMap<>();

    public AnnotationProcessor(String basePackage) {
        this.basePackage = basePackage;
    }

    public void process() {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> domainClasses = reflections.getTypesAnnotatedWith(Domain.class);

        for (Class<?> domainClass : domainClasses) {
            processDomainClass(domainClass);
        }
    }

    private void processDomainClass(Class<?> domainClass) {
        Domain domain = domainClass.getAnnotation(Domain.class);

        // Build ClassModel
        List<String> attributes = Arrays.stream(domainClass.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toList());

        List<String> methods = Arrays.stream(domainClass.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toList());

        ClassModel model = ClassModel.builder()
                .packageName(domainClass.getPackageName())
                .className(domainClass.getSimpleName())
                .domain(domain.name())
                .description(domain.description())
                .attributes(attributes)
                .methods(methods)
                .build();

        classModels.add(model);

        // Scan @Vocabulary fields
        Arrays.stream(domainClass.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Vocabulary.class))
                .forEach(f -> {
                    Vocabulary v = f.getAnnotation(Vocabulary.class);
                    VocabularyModel vocabularyModel = new VocabularyModel(
                            f.getName(),
                            v.alias().isEmpty() ? f.getName() : v.alias(),
                            v.description(),
                            domain.name()
                    );
                    VocabularyRegistry.getInstance().register(vocabularyModel);
                });

        // Scan @Callback methods
        Arrays.stream(domainClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Callback.class))
                .forEach(m -> {
                    Callback cb = m.getAnnotation(Callback.class);
                    callbacks.put(cb.event(), new CallbackRegistry(cb.event(), m, domainClass));
                });

        // Scan @Rule methods
        Arrays.stream(domainClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Rule.class))
                .forEach(m -> {
                    Rule rule = m.getAnnotation(Rule.class);
                    rules.put(rule.name(), new RuleRegistry(rule.name(), rule.subject(), rule.channel(), rule.priority(), m, domainClass));
                });

        // Scan @Event methods
        Arrays.stream(domainClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Event.class))
                .forEach(m -> {
                    Event ev = m.getAnnotation(Event.class);
                    events.put(ev.name(), new EventRegistry(ev.name(), ev.description(), m, domainClass));
                });
    }

    public List<ClassModel> getClassModels() {
        return classModels;
    }

    public Collection<VocabularyModel> getVocabularyModels() {
        return VocabularyRegistry.getInstance().getAll();
    }

    public Map<String, CallbackRegistry> getCallbacks() {
        return callbacks;
    }

    public Map<String, RuleRegistry> getRules() {
        return rules;
    }

    public Map<String, EventRegistry> getEvents() {
        return events;
    }
}
