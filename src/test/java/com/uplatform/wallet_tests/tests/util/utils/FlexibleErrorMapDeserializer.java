package com.uplatform.wallet_tests.tests.util.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FlexibleErrorMapDeserializer extends JsonDeserializer<Map<String, List<String>>> {

    @Override
    public Map<String, List<String>> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken currentToken = p.currentToken();
        TypeFactory typeFactory = ctxt.getTypeFactory();

        if (currentToken == JsonToken.START_ARRAY) {
            while (p.nextToken() != JsonToken.END_ARRAY) {
            }
            return Collections.emptyMap();
        } else if (currentToken == JsonToken.START_OBJECT) {
            ObjectMapper mapper = (ObjectMapper) p.getCodec();

            JavaType keyType = typeFactory.constructType(String.class);
            CollectionType listValueType = typeFactory.constructCollectionType(List.class, String.class);
            MapType targetMapType = typeFactory.constructMapType(Map.class, keyType, listValueType);

            return mapper.readValue(p, targetMapType);
        } else if (currentToken == JsonToken.VALUE_NULL) {
            return null;
        }

        JavaType keyTypeForError = typeFactory.constructType(String.class);
        CollectionType listValueTypeForError = typeFactory.constructCollectionType(List.class, String.class);
        MapType overallTargetType = typeFactory.constructMapType(Map.class, keyTypeForError, listValueTypeForError);

        return (Map<String, List<String>>) ctxt.handleUnexpectedToken(overallTargetType, p);
    }
}