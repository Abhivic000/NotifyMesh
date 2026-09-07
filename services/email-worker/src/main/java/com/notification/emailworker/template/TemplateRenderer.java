package com.notification.emailworker.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safe variable substitution only (PRD section 28: "never execute arbitrary code from
 * templates") - plain {{var}} replacement against the event's JSON payload, nothing
 * more. A missing variable renders as an empty string rather than throwing, so a
 * malformed/incomplete payload degrades the message instead of blocking delivery.
 */
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
