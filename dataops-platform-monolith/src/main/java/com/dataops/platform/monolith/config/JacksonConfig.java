package com.dataops.platform.monolith.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import javax.xml.stream.XMLInputFactory;

/**
 * Centralized Jackson ObjectMapper configuration.
 * Ensures consistent JSON/XML serialization across all modules.
 *
 * Security considerations:
 * - XXE (XML External Entity) protection enabled for XmlMapper
 * - Safe defaults for deserialization
 */
@Slf4j
@Configuration
public class JacksonConfig {

    /**
     * Primary ObjectMapper bean for JSON serialization/deserialization
     * Used by Spring for HTTP message conversion and general JSON handling.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        log.debug("Configuring primary ObjectMapper");
        ObjectMapper mapper = builder.build();

        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

        log.debug("ObjectMapper configured with Java 8 Time support and safe defaults");
        return mapper;
    }

    /**
     * ObjectMapper for JSON processing with pretty printing (exports, responses)
     * Use this for user-facing JSON output that requires formatting.
     */
    @Bean(name = "prettyObjectMapper")
    public ObjectMapper prettyObjectMapper(Jackson2ObjectMapperBuilder builder) {
        log.debug("Configuring pretty ObjectMapper");
        ObjectMapper mapper = builder.build();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        log.debug("Pretty ObjectMapper configured");
        return mapper;
    }

    /**
     * XmlMapper for XML serialization/deserialization
     * Configured with XXE (XML External Entity) protection.
     */
    @Bean
    public XmlMapper xmlMapper() {
        log.debug("Configuring XmlMapper with XXE protection");
        XmlMapper mapper = new XmlMapper();

        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        mapper.getFactory().setXMLInputFactory(xmlInputFactory);

        log.info("XXE protection enabled for XmlMapper using secure XML input factory settings");
        return mapper;
    }
}
