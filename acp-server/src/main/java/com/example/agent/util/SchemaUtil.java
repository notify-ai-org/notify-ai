package com.example.agent.util;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.time.temporal.Temporal;
import java.util.*;

/**
 * Utility to derive a GenAI/ADK {@link Schema} from a Java class using reflection.
 * This is intentionally conservative – it covers common scalar and collection
 * types and treats complex/nested objects as opaque OBJECTs.
 *
 * Usage:
 *  Schema input = SchemaUtil.schemaForClass(MyRequest.class, "MyRequest", "Request schema");
 */
public final class SchemaUtil {

    private SchemaUtil() {}

    public static Schema schemaForClass(Class<?> clazz, String title, String description) {
        Schema.Builder builder = Schema.builder()
            .title(title != null ? title : clazz.getSimpleName())
            .type(Type.Known.OBJECT)
            .description(description != null ? description : ("Schema for " + clazz.getName()));

        Map<String, Schema> props = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            String name = f.getName();
            Schema fieldSchema = schemaForType(f.getGenericType());
            props.put(name, fieldSchema);
        }
        builder.properties(props);
        return builder.build();
    }

    private static Schema schemaForType(java.lang.reflect.Type type) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            if (String.class.equals(cls) || Enum.class.isAssignableFrom(cls)) {
                return Schema.builder().type(Type.Known.STRING).build();
            }
            if (Boolean.class.equals(cls) || boolean.class.equals(cls)) {
                return Schema.builder().type(Type.Known.BOOLEAN).build();
            }
            if (Number.class.isAssignableFrom(cls)
                || cls.equals(int.class) || cls.equals(long.class) || cls.equals(short.class)
                || cls.equals(double.class) || cls.equals(float.class)) {
                return Schema.builder().type(Type.Known.NUMBER).build();
            }
            if (Temporal.class.isAssignableFrom(cls) || java.util.Date.class.isAssignableFrom(cls)) {
                return Schema.builder()
                    .type(Type.Known.STRING)
                    .description("ISO-8601 timestamp")
                    .build();
            }
            if (Collection.class.isAssignableFrom(cls)) {
                // Fallback when generic type information is not available
                return Schema.builder()
                    .type(Type.Known.ARRAY)
                    .items(Schema.builder().type(Type.Known.OBJECT).build())
                    .build();
            }
            if (Map.class.isAssignableFrom(cls)) {
                return Schema.builder().type(Type.Known.OBJECT).build();
            }
            // Complex / nested object – treat as opaque OBJECT
            return Schema.builder().type(Type.Known.OBJECT).build();
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            java.lang.reflect.Type raw = pt.getRawType();
            if (raw instanceof Class && Collection.class.isAssignableFrom((Class<?>) raw)) {
                java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                Schema itemSchema = (args.length == 1) ? schemaForType(args[0])
                        : Schema.builder().type(Type.Known.OBJECT).build();
                return Schema.builder()
                    .type(Type.Known.ARRAY)
                    .items(itemSchema)
                    .build();
            }
            if (raw instanceof Class && Map.class.isAssignableFrom((Class<?>) raw)) {
                // For Map<String, V> treat as object with arbitrary properties
                return Schema.builder().type(Type.Known.OBJECT).build();
            }
        }

        if (type instanceof TypeVariable) {
            return Schema.builder().type(Type.Known.OBJECT).build();
        }

        return Schema.builder().type(Type.Known.OBJECT).build();
    }
}

