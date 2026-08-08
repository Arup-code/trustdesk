package app.dexcode.trustdesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ToolCatalog {

    private final Map<String, ToolDefinition> definitions;

    public ToolCatalog(@Value("${app.seed.data-dir:../data}") String dataDir) {
        ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        File catalogFile = new File(dataDir, "tool_actions.json");
        if (!catalogFile.exists()) {
            // Mirrors DataSeeder's convention: a missing seed-data file means "nothing to load
            // here", not a fatal error. Several tests point app.seed.data-dir at directories that
            // intentionally don't contain the full seed-data set (e.g. only tickets/customers/orders).
            this.definitions = Map.of();
            return;
        }
        try {
            ToolDefinition[] catalog = mapper.readValue(catalogFile, ToolDefinition[].class);
            this.definitions = Arrays.stream(catalog)
                .collect(Collectors.toMap(ToolDefinition::toolName, d -> d));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load tool catalog from " + dataDir, e);
        }
    }

    public Optional<ToolDefinition> find(String toolName) {
        return Optional.ofNullable(definitions.get(toolName));
    }

    public record ToolDefinition(
        String toolName,
        String description,
        String riskLevel,
        boolean requiresHumanApproval,
        List<String> allowedCategories,
        List<String> requiredFields,
        Integer maxAmountInr
    ) {}
}
