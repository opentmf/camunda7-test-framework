package org.opentmf.camunda.test.unit;


import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.cibseven.bpm.client.task.impl.ExternalTaskImpl;
import org.cibseven.bpm.client.variable.impl.DefaultValueMappers;
import org.cibseven.bpm.client.variable.impl.TypedValueField;
import org.cibseven.bpm.client.variable.impl.TypedValues;
import org.cibseven.bpm.client.variable.impl.ValueMappers;
import org.cibseven.bpm.client.variable.impl.format.json.JacksonJsonDataFormat;
import org.cibseven.bpm.client.variable.impl.mapper.*;
import org.cibseven.bpm.engine.variable.Variables;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests can inherit from this class and call buildExternalTask method.
 *
 * @author Yusuf Bozkurt
 */
public abstract class BaseBpmUnitTest {
  /** Source of fake process-instance ids: ten numeric digits, unique per test instance. */
  private static final AtomicLong PROCESS_ID_SEQUENCE = new AtomicLong(1_000_000_000L);

  private static final String JSON_SERIALIZATION_FORMAT =
      Variables.SerializationDataFormats.JSON.getName();
  private final ValueMappers<?> valueMappers = buildValueMappers();
  private final TypedValues typedValues = new TypedValues(valueMappers);

  protected final String camundaProcessId =
      String.valueOf(PROCESS_ID_SEQUENCE.getAndIncrement());

  protected ExternalTaskImpl buildExternalTask(Map<String, Object> variableMap) {
    Map<String, TypedValueField> typedValueFields = getTypedValueFieldMap(variableMap);
    var externalTask = new ExternalTaskImpl();
    externalTask.setVariables(typedValueFields);
    externalTask.setProcessInstanceId(camundaProcessId);
    setReceivedVariableMap(externalTask, typedValueFields);
    return externalTask;
  }

  private void setReceivedVariableMap(
      ExternalTaskImpl externalTask, Map<String, TypedValueField> typedValueFields) {
    externalTask.setReceivedVariableMap(typedValues.wrapVariables(externalTask, typedValueFields));
  }

  private Map<String, TypedValueField> getTypedValueFieldMap(Map<String, Object> variables) {
    return typedValues.serializeVariables(variables);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private ValueMappers buildValueMappers() {
    var mappers = new DefaultValueMappers(JSON_SERIALIZATION_FORMAT);
    mappers.addMapper(new NullValueMapper());
    mappers.addMapper(new BooleanValueMapper());
    mappers.addMapper(new StringValueMapper());
    mappers.addMapper(new ByteArrayValueMapper());
    // number mappers
    mappers.addMapper(new IntegerValueMapper());
    mappers.addMapper(new LongValueMapper());
    mappers.addMapper(new ShortValueMapper());
    mappers.addMapper(new DoubleValueMapper());
    // java.time support is built into Jackson 3; dates stay ISO-8601 strings
    var objectMapper =
        JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    mappers.addMapper(new ObjectValueMapper(
        "application/json",
        new JacksonJsonDataFormat("json", objectMapper)));
    mappers.addMapper(new JsonValueMapper());
    return mappers;
  }
}
