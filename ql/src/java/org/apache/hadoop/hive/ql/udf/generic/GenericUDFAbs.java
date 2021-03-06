/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.udf.generic;

import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentLengthException;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedExpressions;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.FuncAbsDoubleToDouble;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.FuncAbsLongToLong;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.serde2.io.DoubleWritable;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector.Category;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorConverters;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorConverters.Converter;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector.PrimitiveCategory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorUtils;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;

/**
 * GenericUDFAbs.
 *
 */
@Description(name = "abs",
    value = "_FUNC_(x) - returns the absolute value of x",
    extended = "Example:\n"
        + "  > SELECT _FUNC_(0) FROM src LIMIT 1;\n"
        + "  0\n"
        + "  > SELECT _FUNC_(-5) FROM src LIMIT 1;\n" + "  5")
@VectorizedExpressions({FuncAbsLongToLong.class, FuncAbsDoubleToDouble.class})
public class GenericUDFAbs extends GenericUDF {
  private transient PrimitiveCategory inputType;
  private final DoubleWritable resultDouble = new DoubleWritable();
  private final LongWritable resultLong = new LongWritable();
  private final IntWritable resultInt = new IntWritable();
  private transient PrimitiveObjectInspector argumentOI;
  private transient Converter inputConverter;

  @Override
  public ObjectInspector initialize(ObjectInspector[] arguments) throws UDFArgumentException {
    if (arguments.length != 1) {
      throw new UDFArgumentLengthException(
          "ABS() requires 1 argument, got " + arguments.length);
    }

    if (arguments[0].getCategory() != Category.PRIMITIVE) {
      throw new UDFArgumentException(
          "ABS only takes primitive types, got " + arguments[0].getTypeName());
    }
    argumentOI = (PrimitiveObjectInspector) arguments[0];

    inputType = argumentOI.getPrimitiveCategory();
    ObjectInspector outputOI = null;
    switch (inputType) {
    case SHORT:
    case BYTE:
    case INT:
      inputConverter = ObjectInspectorConverters.getConverter(arguments[0],
          PrimitiveObjectInspectorFactory.writableIntObjectInspector);
      outputOI = PrimitiveObjectInspectorFactory.writableIntObjectInspector;
      break;
    case LONG:
      inputConverter = ObjectInspectorConverters.getConverter(arguments[0],
          PrimitiveObjectInspectorFactory.writableLongObjectInspector);
      outputOI = PrimitiveObjectInspectorFactory.writableLongObjectInspector;
      break;
    case FLOAT:
    case STRING:
    case DOUBLE:
      inputConverter = ObjectInspectorConverters.getConverter(arguments[0],
          PrimitiveObjectInspectorFactory.writableDoubleObjectInspector);
      outputOI = PrimitiveObjectInspectorFactory.writableDoubleObjectInspector;
      break;
    case DECIMAL:
      inputConverter = ObjectInspectorConverters.getConverter(arguments[0],
          PrimitiveObjectInspectorFactory.writableHiveDecimalObjectInspector);
      outputOI = PrimitiveObjectInspectorFactory.writableHiveDecimalObjectInspector;
      break;
    default:
      throw new UDFArgumentException(
          "ABS only takes SHORT/BYTE/INT/LONG/DOUBLE/FLOAT/STRING/DECIMAL types, got " + inputType);
    }
    return outputOI;
  }

  @Override
  public Object evaluate(DeferredObject[] arguments) throws HiveException {
    Object valObject = arguments[0].get();
    if (valObject == null) {
      return null;
    }
    switch (inputType) {
    case SHORT:
    case BYTE:
    case INT:
      valObject = inputConverter.convert(valObject);
      resultInt.set(Math.abs(((IntWritable) valObject).get()));
      return resultInt;
    case LONG:
      valObject = inputConverter.convert(valObject);
      resultLong.set(Math.abs(((LongWritable) valObject).get()));
      return resultLong;
    case FLOAT:
    case STRING:
    case DOUBLE:
      valObject = inputConverter.convert(valObject);
      resultDouble.set(Math.abs(((DoubleWritable) valObject).get()));
      return resultDouble;
    case DECIMAL:
      return PrimitiveObjectInspectorFactory.writableHiveDecimalObjectInspector.set(
          PrimitiveObjectInspectorFactory.writableHiveDecimalObjectInspector
              .create(HiveDecimal.ZERO),
          PrimitiveObjectInspectorUtils.getHiveDecimal(valObject,
              argumentOI).abs());
    default:
      throw new UDFArgumentException(
          "ABS only takes SHORT/BYTE/INT/LONG/DOUBLE/FLOAT/STRING/DECIMAL types, got " + inputType);
    }
  }

  @Override
  public String getDisplayString(String[] children) {
    StringBuilder sb = new StringBuilder();
    sb.append("abs(");
    if (children.length > 0) {
      sb.append(children[0]);
      for (int i = 1; i < children.length; i++) {
        sb.append(",");
        sb.append(children[i]);
      }
    }
    sb.append(")");
    return sb.toString();
  }

}
