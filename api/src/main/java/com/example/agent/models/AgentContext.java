package com.example.agent.models;

import java.util.Set;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;

public class AgentContext {

    // ============================================================
    // --- Identity & Security (like Spring SecurityContext)
    // ============================================================

    private Session session;                 // Holds session details              // Authenticated user ID
    private Set<String> roles;               // Role-based ACL
    private String authToken;                // Token for calling external services

    private String idempotencyKey;

    private int schemaVersion;

    private String source;

    private String correlationId;

    private Content content;

    /**
     * @return the content
     */
    public Content getContent() {
        return content;
    }
    /**
     * @param content the content to set
     */
    public void setContent(Content content) {
        this.content = content;
    }
    // Builder methods for new fields
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public static class AgentContextBuilder {
        private AgentContext ctx;

        public AgentContextBuilder() {
            this.ctx = new AgentContext();
        }

        public AgentContextBuilder session(Session session) {
            ctx.setSession(session);
            return this;
        }

        public AgentContextBuilder roles(Set<String> roles) {
            ctx.setRoles(roles);
            return this;
        }

        public AgentContextBuilder authToken(String token) {
            ctx.setAuthToken(token);
            return this;
        }

        public AgentContextBuilder idempotencyKey(String idempotencyKey) {
            ctx.setIdempotencyKey(idempotencyKey);
            return this;
        }

        public AgentContextBuilder schemaVersion(int schemaVersion) {
            ctx.setSchemaVersion(schemaVersion);
            return this;
        }

        public AgentContextBuilder source(String source) {
            ctx.setSource(source);
            return this;
        }

        public AgentContextBuilder correlationId(String correlationId) {
            ctx.setCorrelationId(correlationId);
            return this;
        }

        public AgentContext build() {
            return ctx;
        }
    }

    // ============================================================
    // --- Getters & Setters
    // ============================================================

    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

   
}

