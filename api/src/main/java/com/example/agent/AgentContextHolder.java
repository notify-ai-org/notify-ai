package com.example.agent;

import com.example.agent.models.AgentContext;

public class AgentContextHolder {

    private static final ThreadLocal<AgentContext> CTX = new ThreadLocal<>();

    public static void setContext(AgentContext context) {
        CTX.set(context);
    }

    public static AgentContext getContext() {
        AgentContext ctx = CTX.get();
        if (ctx == null) {
            ctx = new AgentContext();
            CTX.set(ctx);
        }
        return ctx;
    }

    public static void clear() {
        CTX.remove();
    }
}
