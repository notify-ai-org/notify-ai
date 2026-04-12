package com.notify.agent.exceptions;

import java.util.List;

/**
 * Exception thrown when attempting to dispatch or schedule an entity
 * that has not been validated by a human reviewer.
 */
public class ValidationRequiredException extends RuntimeException {

    private final List<String> unvalidatedEntities;
    private final String entityType;

    public ValidationRequiredException(String entityType, String entityId) {
        super(String.format("%s with ID '%s' requires validation before proceeding", entityType, entityId));
        this.entityType = entityType;
        this.unvalidatedEntities = List.of(entityId);
    }

    public ValidationRequiredException(String entityType, List<String> unvalidatedEntityIds) {
        super(String.format("%s entities require validation: %s", entityType, String.join(", ", unvalidatedEntityIds)));
        this.entityType = entityType;
        this.unvalidatedEntities = unvalidatedEntityIds;
    }

    public List<String> getUnvalidatedEntities() {
        return unvalidatedEntities;
    }

    public String getEntityType() {
        return entityType;
    }
}
