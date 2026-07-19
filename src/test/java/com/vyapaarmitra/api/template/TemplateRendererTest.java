package com.vyapaarmitra.api.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.vyapaarmitra.api.template.TemplateRenderer.RenderResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    @Test
    void replacesKnownPlaceholders() {
        RenderResult result = TemplateRenderer.render(
            "Namaste {{customer_name}}, {{amount_due}} due hai.",
            Map.of("customer_name", "Ramesh", "amount_due", "₹500"));
        assertThat(result.ok()).isTrue();
        assertThat(result.text()).isEqualTo("Namaste Ramesh, ₹500 due hai.");
    }

    @Test
    void toleratesWhitespaceInsidePlaceholders() {
        RenderResult result = TemplateRenderer.render("Hi {{ customer_name }}!",
            Map.of("customer_name", "Sita"));
        assertThat(result.text()).isEqualTo("Hi Sita!");
    }

    @Test
    void reportsMissingVariablesAndKeepsPlaceholder() {
        RenderResult result = TemplateRenderer.render(
            "{{customer_name}} due on {{due_date}}", Map.of("customer_name", "Ramesh"));
        assertThat(result.ok()).isFalse();
        assertThat(result.missingVariables()).containsExactly("due_date");
        assertThat(result.text()).isEqualTo("Ramesh due on {{due_date}}");
    }

    @Test
    void leavesPlainTextUntouched() {
        RenderResult result = TemplateRenderer.render("No placeholders here.", Map.of());
        assertThat(result.ok()).isTrue();
        assertThat(result.text()).isEqualTo("No placeholders here.");
    }
}
