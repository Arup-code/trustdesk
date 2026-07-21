package app.dexcode.trustdesk.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

import java.util.List;
import java.util.Map;

public class JsonConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class StringListConverter implements AttributeConverter<List<String>, String> {
        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            try {
                return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize list to JSON", e);
            }
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return List.of();
            try {
                return MAPPER.readValue(dbData, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize JSON to list", e);
            }
        }
    }

    public static class ObjectListConverter implements AttributeConverter<List<Map<String, Object>>, String> {
        @Override
        public String convertToDatabaseColumn(List<Map<String, Object>> attribute) {
            try {
                return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize list to JSON", e);
            }
        }

        @Override
        public List<Map<String, Object>> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return List.of();
            try {
                return MAPPER.readValue(dbData, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize JSON to list", e);
            }
        }
    }

    public static class StringObjectMapConverter implements AttributeConverter<Map<String, Object>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, Object> attribute) {
            try {
                return MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize map to JSON", e);
            }
        }

        @Override
        public Map<String, Object> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return Map.of();
            try {
                return MAPPER.readValue(dbData, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize JSON to map", e);
            }
        }
    }
}
