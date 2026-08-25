package org.opentmf.camunda.test.unit;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.cibseven.bpm.client.task.impl.ExternalTaskImpl;
import org.cibseven.bpm.client.variable.impl.DefaultValueMappers;
import org.cibseven.bpm.client.variable.impl.TypedValueField;
import org.cibseven.bpm.client.variable.impl.TypedValues;
import org.cibseven.bpm.client.variable.impl.ValueMappers;
import org.cibseven.bpm.client.variable.impl.format.json.JacksonJsonDataFormat;
import org.cibseven.bpm.client.variable.impl.mapper.*;
import org.cibseven.bpm.engine.variable.Variables;

/**
 * Unit tests can inherit from this class and call buildExternalTask method.
 *
 * @author Yusuf Bozkurt
 */
public abstract class BaseBpmUnitTest {
  private static final String JSON_SERIALIZATION_FORMAT =
      Variables.SerializationDataFormats.JSON.getName();
  private final ValueMappers<?> valueMappers = buildValueMappers();
  private final TypedValues typedValues = new TypedValues(valueMappers);

  protected final String camundaProcessId = RandomStringUtils.insecure().nextNumeric(10);

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
    var objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mappers.addMapper(new ObjectValueMapper(
        "application/json",
        new JacksonJsonDataFormat("json", objectMapper)));
    mappers.addMapper(new JsonValueMapper());
    return mappers;
  }
}
