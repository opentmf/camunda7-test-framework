package org.opentmf.camunda.test.util;

import java.util.HashMap;
import java.util.Map;
import org.cibseven.bpm.client.variable.impl.TypedValueField;

/**
 * @author Gokhan Demir
 */
public final class VariableUtil {

  private VariableUtil() {}

  /**
   * If the sent string is null or less than 4000 bytes, returns the string, otherwise
   * creates a typedValueField and encapsulates the string within that field
   * so that it can be persisted as a Camunda WFF variable with the help of the Spin plugin.
   * @param s The requested string value of a Camunda variable.
   * @return either the string itself or encapsulated form inside a TypedValueField.
   * @see TypedValueField
   * @see org.cibseven.spin.plugin.impl.SpinProcessEnginePlugin
   */
  public static Object stringVariable(String s) {
    if (s == null || s.length() <= 4000) {
      return s;
    }
    var typedValueField = new TypedValueField();
    typedValueField.setType("Object");
    typedValueField.setValue(s);
    Map<String, Object> valueInfo = new HashMap<>();
    valueInfo.put("objectTypeName", String.class.getName());
    valueInfo.put("serializationDataFormat", "application/json");
    typedValueField.setValueInfo(valueInfo);
    return typedValueField;
  }
}
