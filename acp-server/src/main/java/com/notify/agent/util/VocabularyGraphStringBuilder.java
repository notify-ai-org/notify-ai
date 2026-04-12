package com.notify.agent.util;

import com.notify.agent.models.Vocabulary;

import java.util.*;

public class VocabularyGraphStringBuilder {

    private static final int MAX_DEPTH = 8;

    public String build(Collection<Vocabulary> vocabularies) {
        Map<Vocabulary, List<Vocabulary>> tree = buildTree(vocabularies);
        StringBuilder sb = new StringBuilder();

        sb.append("/vocabulary\n");

        List<Vocabulary> roots = tree.keySet().stream()
                .filter(v -> v.getParent() == null)
                .sorted(Comparator.comparing(Vocabulary::getTerm))
                .toList();

        for (Vocabulary root : roots) {
            renderNode(sb, root, tree, 1, new HashSet<>());
        }

        return sb.toString();
    }

    private Map<Vocabulary, List<Vocabulary>> buildTree(
            Collection<Vocabulary> vocabularies) {

        Map<Vocabulary, List<Vocabulary>> tree = new HashMap<>();

        for (Vocabulary v : vocabularies) {
            tree.computeIfAbsent(v, k -> new ArrayList<>());
        }

        for (Vocabulary v : vocabularies) {
            Vocabulary parent = v.getParent();
            if (parent != null) {
                tree.computeIfAbsent(parent, k -> new ArrayList<>())
                        .add(v);
            }
        }

        // Sort children deterministically
        for (List<Vocabulary> children : tree.values()) {
            children.sort(Comparator.comparing(Vocabulary::getTerm));
        }

        return tree;
    }

    private void renderNode(
            StringBuilder sb,
            Vocabulary node,
            Map<Vocabulary, List<Vocabulary>> tree,
            int depth,
            Set<Vocabulary> visited) {

        if (depth > MAX_DEPTH) {
            indent(sb, depth);
            sb.append("└── ").append(node.getTerm())
                    .append("  [depth limit reached]\n");
            return;
        }

        if (!visited.add(node)) {
            indent(sb, depth);
            sb.append("└── ").append(node.getTerm())
                    .append("  [cycle detected]\n");
            return;
        }

        indent(sb, depth);
        sb.append("├── ").append(node.getTerm());

        appendMetadata(sb, node);
        sb.append("\n");

        List<Vocabulary> children = tree.get(node);
        if (children != null) {
            for (Vocabulary child : children) {
                renderNode(sb, child, tree, depth + 1, visited);
            }
        }

        visited.remove(node);
    }

    private void appendMetadata(StringBuilder sb, Vocabulary v) {
        boolean hasMeta = false;

        if (v.getType() != null) {
            sb.append("  [type=").append(v.getType()).append("]");
            hasMeta = true;
        }

        if (v.getCurrentValue() != null) {
            sb.append(hasMeta ? " = " : " = ");
            sb.append(renderValue(v.getCurrentValue()));
        }
    }

    private String renderValue(Object value) {
        if (value instanceof String s) {
            return "\"" + s + "\"";
        }
        return String.valueOf(value);
    }

    private void indent(StringBuilder sb, int depth) {
        sb.append("│   ".repeat(Math.max(0, depth - 1)));
    }
}




