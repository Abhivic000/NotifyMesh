package com.notification.pushworker.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Same as email-worker's renderer - duplicated rather than shared for now (see Phase 5 write-up). */
@Component
public class TemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    public String render(String template, JsonNode payload) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String value = payload.path(variableName).asText("");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
