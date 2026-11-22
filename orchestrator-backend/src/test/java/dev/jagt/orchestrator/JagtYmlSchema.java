package dev.jagt.orchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

public final class JagtYmlSchema {

  private static final Pattern DASHED = Pattern.compile("-([a-z])");
  private static final Pattern LOOKED_UP = Pattern.compile("[\"{]orchestrator\\.([a-z][a-z-]*)");
  private static final Pattern CONDITIONAL =
      Pattern.compile("prefix = \"orchestrator\",\\s+name = \"([a-z][a-z-]*)\"");
  private static final String UNWRAPPED = "x-unwrapped";

  private JagtYmlSchema() {}

  public static Schema compiled() {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        .getSchema(generate().toString());
  }

  private static SchemaGeneratorConfig config() {
    SchemaGeneratorConfigBuilder builder =
        new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
            .with(Option.INLINE_ALL_SCHEMAS, Option.NONPUBLIC_NONSTATIC_FIELDS_WITHOUT_GETTERS)
            .without(Option.SCHEMA_VERSION_INDICATOR, Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES);
    builder
        .forFields()
        .withPropertyNameOverrideResolver(field ->
            field.getAnnotationConsideringFieldAndGetter(JsonProperty.class) == null
                ? null
                : field.getAnnotationConsideringFieldAndGetter(JsonProperty.class).value())
        .withInstanceAttributeOverride((node, field, context) -> {
          if (field.getAnnotationConsideringFieldAndGetter(JsonUnwrapped.class) != null) {
            node.put(UNWRAPPED, true);
          }
        });
    builder
        .forTypesInGeneral()
        .withAdditionalPropertiesResolver(scope ->
            scope.getType().isInstanceOf(Map.class)
                ? scope.getTypeParameterFor(Map.class, 1)
                : Void.class)
        .withCustomDefinitionProvider(
            (type, context) -> definition(type.getErasedType(), context.getGeneratorConfig()));
    return builder.build();
  }

  public static ObjectNode generate() {
    SchemaGeneratorConfig config = config();
    ObjectNode root = config.createObjectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    root.putObject("properties").putObject("server").put("type", "object");
    SchemaGenerator generator = new SchemaGenerator(config);
    ObjectNode file = unwrapped(generator.generateSchema(ConfigFile.class));
    ObjectNode fileProperties = (ObjectNode) file.get("properties");
    ((ObjectNode) fileProperties.get("roles"))
        .withObjectProperty("properties")
        .set("master", fileProperties.get("master"));
    mount(root, "orchestrator", file);
    SourceRoots.ALL.stream()
        .filter(type -> type.isAnnotatedWith(ConfigurationProperties.class))
        .map(type -> type.reflect())
        .forEach(type ->
            mount(
                root,
                DASHED
                    .matcher(type.getAnnotation(ConfigurationProperties.class).prefix())
                    .replaceAll(letter -> letter.group(1).toUpperCase(Locale.ROOT)),
                generator.generateSchema(type)));
    readByName(root);
    return root;
  }

  private static @Nullable CustomDefinition definition(
      Class<?> type, SchemaGeneratorConfig config) {
    ObjectNode definition = config.createObjectNode();
    if (type == Duration.class) {
      definition.putArray("type").add("string").add("integer");
      return new CustomDefinition(definition);
    }
    if (!type.isEnum()) {
      return null;
    }
    definition.put("type", "string");
    ArrayNode names = definition.putArray("enum");
    Arrays.stream(type.getEnumConstants())
        .forEach(constant -> names.add(lowerKebab((Enum<?>) constant)));
    return new CustomDefinition(definition);
  }

  private static String lowerKebab(Enum<?> constant) {
    return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static void readByName(ObjectNode root) {
    ObjectNode properties =
        root.withObjectProperty("properties")
            .withObjectProperty("orchestrator")
            .withObjectProperty("properties");
    Stream<String> defaults =
        new YAMLMapper()
                .readTree(SourceRoots.read(Path.of("src/main/resources/application.yml")))
                .path("orchestrator")
                .propertyNames()
                .stream();
    Stream<String> looked =
        SourceRoots.mainJavaFiles()
            .map(SourceRoots::read)
            .flatMap(source ->
                Stream.concat(
                    LOOKED_UP.matcher(source).results(), CONDITIONAL.matcher(source).results()))
            .map(match -> match.group(1));
    Stream.concat(defaults, looked)
        .map(key ->
            DASHED.matcher(key).replaceAll(letter -> letter.group(1).toUpperCase(Locale.ROOT)))
        .filter(key -> !properties.has(key))
        .forEach(key -> properties.putObject(key));
  }

  private static ObjectNode unwrapped(ObjectNode schema) {
    if (schema.get("additionalProperties") instanceof ObjectNode values) {
      unwrapped(values);
    }
    if (schema.get("properties") instanceof ObjectNode properties) {
      for (Map.Entry<String, JsonNode> property : List.copyOf(properties.properties())) {
        if (property.getValue() instanceof ObjectNode child
            && unwrapped(child).path(UNWRAPPED).asBoolean()) {
          properties.remove(property.getKey());
          child
              .get("properties")
              .properties()
              .forEach(entry -> properties.set(entry.getKey(), entry.getValue()));
        }
      }
    }
    return schema;
  }

  private static void mount(ObjectNode root, String prefix, ObjectNode schema) {
    ObjectNode target = root;
    for (String segment : prefix.split("\\.", -1)) {
      target = target.withObjectProperty("properties").withObjectProperty(segment);
      target.put("type", "object");
      target.put("additionalProperties", false);
    }
    ObjectNode properties = target.withObjectProperty("properties");
    schema
        .get("properties")
        .properties()
        .forEach(entry -> properties.set(entry.getKey(), entry.getValue()));
  }
}
