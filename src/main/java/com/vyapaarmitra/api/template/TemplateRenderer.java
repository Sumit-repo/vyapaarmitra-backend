package com.vyapaarmitra.api.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders {@code {{placeholder}}} variables in template bodies. Unknown or
 * missing-value placeholders are reported so the FRD rule "block send until
 * fixed" can be enforced by the caller.
 */
public final class TemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z_]+)\\s*}}");

    private TemplateRenderer() {
    }

    public record RenderResult(String text, List<String> missingVariables) {

        public boolean ok() {
            return missingVariables.isEmpty();
        }
    }

    public static RenderResult render(String body, Map<String, String> variables) {
        List<String> missing = new ArrayList<>();
        Matcher matcher = VARIABLE.matcher(body);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                missing.add(key);
                value = matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return new RenderResult(result.toString(), List.copyOf(missing));
    }
}
