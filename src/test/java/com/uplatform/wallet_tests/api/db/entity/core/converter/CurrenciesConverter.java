package com.uplatform.wallet_tests.api.db.entity.core.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

/**
 * JPA {@link AttributeConverter} for converting a JSON array stored in the
 * {@code currencies} column to a {@link List} of strings and back.
 */
@Converter(autoApply = false)
public class CurrenciesConverter implements AttributeConverter<List<String>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize currencies", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize currencies", e);
        }
    }
}
