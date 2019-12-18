package scalapb_playjson

import com.google.protobuf.ByteString
import com.google.protobuf.descriptor.FieldDescriptorProto
import com.google.protobuf.descriptor.FieldDescriptorProto.Type
import com.google.protobuf.duration.Duration
import com.google.protobuf.field_mask.FieldMask
import com.google.protobuf.struct.NullValue
import com.google.protobuf.timestamp.Timestamp
import play.api.libs.json._
import scalapb_json._
import scalapb_json.ScalapbJsonCommon.unsignedInt

import scala.collection.mutable
import scala.reflect.ClassTag
import scalapb._
import scalapb.descriptors.{Reads => _, _}
import scalapb_json.ScalapbJsonCommon.GenericCompanion
import scala.util.control.NonFatal

case class Formatter[T](writer: (Printer, T) => JsValue, parser: (Parser, JsValue) => T)

case class FormatRegistry(
  messageFormatters: Map[Class[_], Formatter[_]] = Map.empty,
  enumFormatters: Map[EnumDescriptor, EnumFormatter[EnumValueDescriptor]] = Map.empty,
  registeredCompanions: Seq[GenericCompanion] = Seq.empty
) {
  def registerMessageFormatter[T <: GeneratedMessage](
    writer: (Printer, T) => JsValue,
    parser: (Parser, JsValue) => T
  )(implicit ct: ClassTag[T]): FormatRegistry = {
    copy(messageFormatters = messageFormatters + (ct.runtimeClass -> Formatter(writer, parser)))
  }

  def registerEnumFormatter[E <: GeneratedEnum](
    writer: (Printer, EnumValueDescriptor) => JsValue,
    parser: (Parser, JsValue) => Option[EnumValueDescriptor]
  )(implicit cmp: GeneratedEnumCompanion[E]): FormatRegistry = {
    copy(enumFormatters = enumFormatters + (cmp.scalaDescriptor -> EnumFormatter(writer, parser)))
  }

  def registerWriter[T <: GeneratedMessage: ClassTag](
    writer: T => JsValue,
    parser: JsValue => T
  ): FormatRegistry = {
    registerMessageFormatter((p: Printer, t: T) => writer(t), (p: Parser, v: JsValue) => parser(v))
  }

  def getMessageWriter[T](klass: Class[_ <: T]): Option[(Printer, T) => JsValue] = {
    messageFormatters.get(klass).asInstanceOf[Option[Formatter[T]]].map(_.writer)
  }

  def getMessageParser[T](klass: Class[_ <: T]): Option[(Parser, JsValue) => T] = {
    messageFormatters.get(klass).asInstanceOf[Option[Formatter[T]]].map(_.parser)
  }

  def getEnumWriter(
    descriptor: EnumDescriptor
  ): Option[(Printer, EnumValueDescriptor) => JsValue] = {
    enumFormatters.get(descriptor).map(_.writer)
  }

  def getEnumParser(
    descriptor: EnumDescriptor
  ): Option[(Parser, JsValue) => Option[EnumValueDescriptor]] = {
    enumFormatters.get(descriptor).map(_.parser)
  }
}

class Printer(config: Printer.PrinterConfig) {
  def print[A](m: GeneratedMessage): String = {
    toJson(m).toString()
  }

  def this() = this(Printer.defaultConfig)

  @deprecated(
    message =
      "Use new Printer() and chain includingDefaultValueFields, preservingProtoFieldNames, etc.",
    since = "0.12.0-M1"
  )
  def this(
    includingDefaultValueFields: Boolean = Printer.defaultConfig.isIncludingDefaultValueFields,
    preservingProtoFieldNames: Boolean = Printer.defaultConfig.isPreservingProtoFieldNames,
    formattingLongAsNumber: Boolean = Printer.defaultConfig.isFormattingLongAsNumber,
    formattingEnumsAsNumber: Boolean = Printer.defaultConfig.isFormattingEnumsAsNumber,
    formatRegistry: FormatRegistry = Printer.defaultConfig.formatRegistry,
    typeRegistry: TypeRegistry = Printer.defaultConfig.typeRegistry
  ) = this(
    Printer.PrinterConfig(
      isIncludingDefaultValueFields = includingDefaultValueFields,
      isPreservingProtoFieldNames = preservingProtoFieldNames,
      isFormattingLongAsNumber = formattingLongAsNumber,
      isFormattingEnumsAsNumber = formattingEnumsAsNumber,
      formatRegistry = formatRegistry,
      typeRegistry = typeRegistry
    )
  )

  def includingDefaultValueFields: Printer =
    new Printer(config.copy(isIncludingDefaultValueFields = true))

  def preservingProtoFieldNames: Printer =
    new Printer(config.copy(isPreservingProtoFieldNames = true))

  def formattingLongAsNumber: Printer =
    new Printer(config.copy(isFormattingLongAsNumber = true))

  def formattingEnumsAsNumber: Printer =
    new Printer(config.copy(isFormattingEnumsAsNumber = true))

  def withFormatRegistry(formatRegistry: FormatRegistry): Printer =
    new Printer(config.copy(formatRegistry = formatRegistry))

  def withTypeRegistry(typeRegistry: TypeRegistry): Printer =
    new Printer(config.copy(typeRegistry = typeRegistry))

  def typeRegistry: TypeRegistry = config.typeRegistry

  private[this] type JField = (String, JsValue)
  private[this] type FieldBuilder = mutable.Builder[JField, List[JField]]

  private[this] def JField(key: String, value: JsValue) = (key, value)

  private def serializeMessageField(
    fd: FieldDescriptor,
    name: String,
    value: Any,
    b: FieldBuilder
  ): Unit = {
    value match {
      case null =>
      // We are never printing empty optional messages to prevent infinite recursion.
      case Nil =>
        if (config.isIncludingDefaultValueFields) {
          b += ((name, if (fd.isMapField) Json.obj() else JsArray(Nil)))
        }
      case xs: Iterable[GeneratedMessage] @unchecked =>
        if (fd.isMapField) {
          val mapEntryDescriptor = fd.scalaType.asInstanceOf[ScalaType.Message].descriptor
          val keyDescriptor = mapEntryDescriptor.findFieldByNumber(1).get
          val valueDescriptor = mapEntryDescriptor.findFieldByNumber(2).get
          b += JField(
            name,
            JsObject(xs.iterator.map { x =>
              val key = x.getField(keyDescriptor) match {
                case PBoolean(v) => v.toString
                case PDouble(v) => v.toString
                case PFloat(v) => v.toString
                case PInt(v) => v.toString
                case PLong(v) => v.toString
                case PString(v) => v
                case v => throw new JsonFormatException(s"Unexpected value for key: $v")
              }
              val value = if (valueDescriptor.protoType.isTypeMessage) {
                toJson(x.getFieldByNumber(valueDescriptor.number).asInstanceOf[GeneratedMessage])
              } else {
                serializeSingleValue(
                  valueDescriptor,
                  x.getField(valueDescriptor),
                  config.isFormattingLongAsNumber
                )
              }
              key -> value
            }.toList)
          )
        } else {
          b += JField(name, JsArray(xs.iterator.map(toJson).toList))
        }
      case msg: GeneratedMessage =>
        b += JField(name, toJson(msg))
      case v =>
        throw new JsonFormatException(v.toString)
    }
  }

  private def serializeNonMessageField(
    fd: FieldDescriptor,
    name: String,
    value: PValue,
    b: FieldBuilder
  ) = {
    value match {
      case PEmpty =>
        if (config.isIncludingDefaultValueFields && fd.containingOneof.isEmpty) {
          b += JField(name, defaultJsValue(fd))
        }
      case PRepeated(xs) =>
        if (xs.nonEmpty || config.isIncludingDefaultValueFields) {
          b += JField(
            name,
            JsArray(xs.map(serializeSingleValue(fd, _, config.isFormattingLongAsNumber)))
          )
        }
      case v =>
        if (config.isIncludingDefaultValueFields ||
          !fd.isOptional ||
          !fd.file.isProto3 ||
          (v != scalapb_json.ScalapbJsonCommon.defaultValue(fd)) ||
          fd.containingOneof.isDefined) {
          b += JField(name, serializeSingleValue(fd, v, config.isFormattingLongAsNumber))
        }
    }
  }

  def toJson[A <: GeneratedMessage](m: A): JsValue = {
    config.formatRegistry.getMessageWriter[A](m.getClass) match {
      case Some(f) => f(this, m)
      case None =>
        val b = List.newBuilder[JField]
        val descriptor = m.companion.scalaDescriptor
        b.sizeHint(descriptor.fields.size)
        descriptor.fields.foreach { f =>
          val name =
            if (config.isPreservingProtoFieldNames) f.name
            else scalapb_json.ScalapbJsonCommon.jsonName(f)
          if (f.protoType.isTypeMessage) {
            serializeMessageField(f, name, m.getFieldByNumber(f.number), b)
          } else {
            serializeNonMessageField(f, name, m.getField(f), b)
          }
        }
        JsObject(b.result())
    }
  }

  private def defaultJsValue(fd: FieldDescriptor): JsValue =
    serializeSingleValue(
      fd,
      scalapb_json.ScalapbJsonCommon.defaultValue(fd),
      config.isFormattingLongAsNumber
    )

  private def unsignedLong(n: Long) =
    if (n < 0) BigDecimal(BigInt(n & 0X7FFFFFFFFFFFFFFFL).setBit(63)) else BigDecimal(n)

  private def formatLong(
    n: Long,
    protoType: FieldDescriptorProto.Type,
    formattingLongAsNumber: Boolean
  ): JsValue = {
    val v =
      if (protoType.isTypeUint64 || protoType.isTypeFixed64) unsignedLong(n) else BigDecimal(n)
    if (formattingLongAsNumber) JsNumber(v) else JsString(v.toString())
  }

  def serializeSingleValue(
    fd: FieldDescriptor,
    value: PValue,
    formattingLongAsNumber: Boolean
  ): JsValue = value match {
    case PEnum(e) =>
      config.formatRegistry.getEnumWriter(e.containingEnum) match {
        case Some(writer) => writer(this, e)
        case None => if (config.isFormattingEnumsAsNumber) JsNumber(e.number) else JsString(e.name)
      }
    case PInt(v) if fd.protoType.isTypeUint32 => JsNumber(unsignedInt(v))
    case PInt(v) if fd.protoType.isTypeFixed32 => JsNumber(unsignedInt(v))
    case PInt(v) => JsNumber(v)
    case PLong(v) => formatLong(v, fd.protoType, formattingLongAsNumber)
    case PDouble(v) => Printer.doubleToJson(v)
    case PFloat(v) => Printer.floatToJson(v)
    case PBoolean(v) => JsBoolean(v)
    case PString(v) => JsString(v)
    case PByteString(v) => JsString(java.util.Base64.getEncoder.encodeToString(v.toByteArray))
    case _: PMessage | PRepeated(_) | PEmpty => throw new RuntimeException("Should not happen")
  }
}

object Printer {
  private[this] val JsStringPosInfinity = JsString("Infinity")
  private[this] val JsStringNegInfinity = JsString("-Infinity")
  private[this] val JsStringNaN = JsString("NaN")

  private final case class PrinterConfig(
    isIncludingDefaultValueFields: Boolean,
    isPreservingProtoFieldNames: Boolean,
    isFormattingLongAsNumber: Boolean,
    isFormattingEnumsAsNumber: Boolean,
    formatRegistry: FormatRegistry,
    typeRegistry: TypeRegistry
  )

  private val defaultConfig = PrinterConfig(
    isIncludingDefaultValueFields = false,
    isPreservingProtoFieldNames = false,
    isFormattingLongAsNumber = false,
    isFormattingEnumsAsNumber = false,
    formatRegistry = JsonFormat.DefaultRegistry,
    typeRegistry = TypeRegistry.empty
  )

  private[scalapb_playjson] def doubleToJson(value: Double): JsValue = {
    if (value.isPosInfinity) {
      JsStringPosInfinity
    } else if (value.isNegInfinity) {
      JsStringNegInfinity
    } else if (java.lang.Double.isNaN(value)) {
      JsStringNaN
    } else {
      JsNumber(value)
    }
  }

  private def floatToJson(value: Float): JsValue = {
    if (value.isPosInfinity) {
      JsStringPosInfinity
    } else if (value.isNegInfinity) {
      JsStringNegInfinity
    } else if (java.lang.Double.isNaN(value)) {
      JsStringNaN
    } else {
      JsNumber(value)
    }
  }
}

object Parser {
  private final case class ParserConfig(
    isIgnoringUnknownFields: Boolean,
    formatRegistry: FormatRegistry,
    typeRegistry: TypeRegistry
  )

  private val defaultConfig = Parser.ParserConfig(
    isIgnoringUnknownFields = false,
    JsonFormat.DefaultRegistry,
    TypeRegistry.empty
  )

  private val memorizedFieldNameMap = new MemorizedFieldNameMap()
}

class Parser(config: Parser.ParserConfig) {
  def this() = this(Parser.defaultConfig)

  @deprecated(
    message = "Use new Parser() and chain with usingTypeRegistry or formatRegistry",
    since = "0.12.0-M1"
  )
  def this(
    preservingProtoFieldNames: Boolean = false,
    formatRegistry: FormatRegistry = JsonFormat.DefaultRegistry,
    typeRegistry: TypeRegistry = TypeRegistry.empty
  ) =
    this(
      Parser.ParserConfig(
        isIgnoringUnknownFields = false,
        formatRegistry,
        typeRegistry
      )
    )

  def ignoringUnknownFields: Parser =
    new Parser(config.copy(isIgnoringUnknownFields = true))

  def withFormatRegistry(formatRegistry: FormatRegistry) =
    new Parser(config.copy(formatRegistry = formatRegistry))

  def withTypeRegistry(typeRegistry: TypeRegistry) =
    new Parser(config.copy(typeRegistry = typeRegistry))

  def typeRegistry: TypeRegistry = config.typeRegistry

  def fromJsonString[A <: GeneratedMessage with Message[A]](
    str: String
  )(implicit cmp: GeneratedMessageCompanion[A]): A = {
    val j =
      try {
        Json.parse(str)
      } catch {
        case e: IllegalArgumentException =>
          throw new JsonFormatException("could not parse json", e)
      }
    fromJson(j)
  }

  def fromJson[A <: GeneratedMessage with Message[A]](
    value: JsValue
  )(implicit cmp: GeneratedMessageCompanion[A]): A = {
    fromJson(value, skipTypeUrl = false)
  }

  private[scalapb_playjson] def fromJson[A <: GeneratedMessage with Message[A]](
    value: JsValue,
    skipTypeUrl: Boolean
  )(implicit cmp: GeneratedMessageCompanion[A]): A = {
    cmp.messageReads.read(fromJsonToPMessage(cmp, value, skipTypeUrl))
  }

  private def serializedName(fd: FieldDescriptor): String = {
    if (config.isIgnoringUnknownFields) fd.asProto.getName
    else scalapb_json.ScalapbJsonCommon.jsonName(fd)
  }

  private def fromJsonToPMessage(
    cmp: GeneratedMessageCompanion[_],
    value: JsValue,
    skipTypeUrl: Boolean
  ): PMessage = {
    def parseValue(fd: FieldDescriptor, value: JsValue): PValue = {
      if (fd.isMapField) {
        value match {
          case JsObject(vals) =>
            val mapEntryDesc = fd.scalaType.asInstanceOf[ScalaType.Message].descriptor
            val keyDescriptor = mapEntryDesc.findFieldByNumber(1).get
            val valueDescriptor = mapEntryDesc.findFieldByNumber(2).get
            PRepeated(vals.iterator.map {
              case (key, jValue) =>
                val keyObj = keyDescriptor.scalaType match {
                  case ScalaType.Boolean => PBoolean(java.lang.Boolean.valueOf(key))
                  case ScalaType.Double => PDouble(java.lang.Double.valueOf(key))
                  case ScalaType.Float => PFloat(java.lang.Float.valueOf(key))
                  case ScalaType.Int => PInt(java.lang.Integer.valueOf(key))
                  case ScalaType.Long => PLong(java.lang.Long.valueOf(key))
                  case ScalaType.String => PString(key)
                  case _ => throw new RuntimeException(s"Unsupported type for key for ${fd.name}")
                }
                PMessage(
                  Map(
                    keyDescriptor -> keyObj,
                    valueDescriptor -> parseSingleValue(
                      cmp.messageCompanionForFieldNumber(fd.number),
                      valueDescriptor,
                      jValue
                    )
                  )
                )
            }.toVector)
          case _ =>
            throw new JsonFormatException(
              s"Expected an object for map field ${serializedName(fd)} of ${fd.containingMessage.name}"
            )
        }
      } else if (fd.isRepeated) {
        value match {
          case JsArray(vals) =>
            PRepeated(vals.iterator.map(parseSingleValue(cmp, fd, _)).toVector)
          case _ =>
            throw new JsonFormatException(
              s"Expected an array for repeated field ${serializedName(fd)} of ${fd.containingMessage.name}"
            )
        }
      } else parseSingleValue(cmp, fd, value)
    }

    config.formatRegistry.getMessageParser(cmp.defaultInstance.getClass) match {
      case Some(p) => p(this, value).asInstanceOf[GeneratedMessage].toPMessage
      case None =>
        value match {
          case JsObject(fields) =>
            val fieldMap = Parser.memorizedFieldNameMap.get(cmp.scalaDescriptor)
            val valueMapBuilder = Map.newBuilder[FieldDescriptor, PValue]
            fields.foreach {
              case (name, jValue) =>
                if (fieldMap.contains(name)) {
                  if (jValue != JsNull) {
                    val fd = fieldMap(name)
                    valueMapBuilder += (fd -> parseValue(fd, jValue))
                  }
                } else if (!config.isIgnoringUnknownFields && !(skipTypeUrl && name == "@type")) {
                  throw new JsonFormatException(
                    s"Cannot find field: ${name} in message ${cmp.scalaDescriptor.fullName}"
                  )
                }
            }

            PMessage(valueMapBuilder.result())
          case _ =>
            throw new JsonFormatException(s"Expected an object, found ${value}")
        }
    }
  }

  def defaultEnumParser(
    enumDescriptor: EnumDescriptor,
    value: JsValue
  ): Option[EnumValueDescriptor] = {
    def enumValueFromInt(v: Int): Option[EnumValueDescriptor] =
      if (enumDescriptor.file.isProto3)
        Some(enumDescriptor.findValueByNumberCreatingIfUnknown(v))
      else
        enumDescriptor.findValueByNumber(v)

    def fail() =
      throw new JsonFormatException(
        s"Invalid enum value: $value for enum type: ${enumDescriptor.fullName}"
      )

    def defaultValue =
      if (config.isIgnoringUnknownFields && enumDescriptor.file.isProto3)
        enumDescriptor.findValueByNumber(0)
      else None

    val res = value match {
      case JsNumber(v) =>
        try {
          enumValueFromInt(v.bigDecimal.intValueExact)
        } catch {
          case _: ArithmeticException => defaultValue
        }
      case JsString(s) =>
        enumDescriptor.values
          .find(_.name == s)
          .orElse {
            try {
              enumValueFromInt(new java.math.BigDecimal(s).intValueExact())
            } catch {
              case _: ArithmeticException => None
              case _: NumberFormatException => None
            }
          }
          .orElse(defaultValue)
      case _ =>
        fail()
    }

    if (res.isEmpty && !config.isIgnoringUnknownFields) {
      fail()
    }

    res
  }

  protected def parseSingleValue(
    containerCompanion: GeneratedMessageCompanion[_],
    fd: FieldDescriptor,
    value: JsValue
  ): PValue = fd.scalaType match {
    case ScalaType.Enum(ed) => {
      config.formatRegistry.getEnumParser(ed) match {
        case Some(parser) =>
          parser(this, value)
        case None =>
          defaultEnumParser(ed, value)
      }
    } match {
      case Some(x) => PEnum(x)
      case None => PEmpty
    }
    case ScalaType.Message(_) =>
      fromJsonToPMessage(
        containerCompanion.messageCompanionForFieldNumber(fd.number),
        value,
        skipTypeUrl = false
      )
    case _ =>
      JsonFormat.parsePrimitive(
        fd.protoType,
        value,
        throw new JsonFormatException(
          s"Unexpected value ($value) for field ${serializedName(fd)} of ${fd.containingMessage.name}"
        )
      )
  }
}

object JsonFormat {
  import com.google.protobuf.wrappers
  import scalapb_json.ScalapbJsonCommon._

  val DefaultRegistry = FormatRegistry()
    .registerWriter(
      (d: Duration) => JsString(Durations.writeDuration(d)), {
        case JsString(str) => Durations.parseDuration(str)
        case _ => throw new JsonFormatException("Expected a string.")
      }
    )
    .registerWriter(
      (t: Timestamp) => JsString(Timestamps.writeTimestamp(t)), {
        case JsString(str) => Timestamps.parseTimestamp(str)
        case _ => throw new JsonFormatException("Expected a string.")
      }
    )
    .registerWriter(
      (f: FieldMask) => JsString(ScalapbJsonCommon.fieldMaskToJsonString(f)), {
        case JsString(str) =>
          ScalapbJsonCommon.fieldMaskFromJsonString(str)
        case _ =>
          throw new JsonFormatException("Expected a string.")
      }
    )
    .registerMessageFormatter[wrappers.DoubleValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.DoubleValue]
    )
    .registerMessageFormatter[wrappers.FloatValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.FloatValue]
    )
    .registerMessageFormatter[wrappers.Int32Value](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.Int32Value]
    )
    .registerMessageFormatter[wrappers.Int64Value](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.Int64Value]
    )
    .registerMessageFormatter[wrappers.UInt32Value](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.UInt32Value]
    )
    .registerMessageFormatter[wrappers.UInt64Value](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.UInt64Value]
    )
    .registerMessageFormatter[wrappers.BoolValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.BoolValue]
    )
    .registerMessageFormatter[wrappers.BytesValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.BytesValue]
    )
    .registerMessageFormatter[wrappers.StringValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.StringValue]
    )
    .registerEnumFormatter[NullValue](
      (_, _) => JsNull,
      (parser, value) =>
        value match {
          case JsNull => Some(NullValue.NULL_VALUE.scalaValueDescriptor)
          case _ => parser.defaultEnumParser(NullValue.scalaDescriptor, value)
        }
    )
    .registerWriter[com.google.protobuf.struct.Value](
      StructFormat.structValueWriter,
      StructFormat.structValueParser
    )
    .registerWriter[com.google.protobuf.struct.Struct](
      StructFormat.structWriter,
      StructFormat.structParser
    )
    .registerWriter[com.google.protobuf.struct.ListValue](
      StructFormat.listValueWriter,
      StructFormat.listValueParser
    )
    .registerMessageFormatter[com.google.protobuf.any.Any](AnyFormat.anyWriter, AnyFormat.anyParser)

  def primitiveWrapperWriter[T <: GeneratedMessage with Message[T]](
    implicit cmp: GeneratedMessageCompanion[T]
  ): ((Printer, T) => JsValue) = {
    val fieldDesc = cmp.scalaDescriptor.findFieldByNumber(1).get
    (printer, t) =>
      printer.serializeSingleValue(fieldDesc, t.getField(fieldDesc), formattingLongAsNumber = false)
  }

  def primitiveWrapperParser[T <: GeneratedMessage with Message[T]](
    implicit cmp: GeneratedMessageCompanion[T]
  ): ((Parser, JsValue) => T) = {
    val fieldDesc = cmp.scalaDescriptor.findFieldByNumber(1).get
    (parser, jv) =>
      cmp.messageReads.read(
        PMessage(
          Map(
            fieldDesc -> JsonFormat.parsePrimitive(
              fieldDesc.protoType,
              jv,
              throw new JsonFormatException(s"Unexpected value for ${cmp.scalaDescriptor.name}")
            )
          )
        )
      )
  }

  val printer = new Printer()
  val parser = new Parser()

  def toJsonString[A <: GeneratedMessage](m: A): String = printer.print(m)

  def toJson[A <: GeneratedMessage](m: A): JsValue = printer.toJson(m)

  def fromJson[A <: GeneratedMessage with Message[A]: GeneratedMessageCompanion](
    value: JsValue
  ): A = {
    parser.fromJson(value)
  }

  def fromJsonString[A <: GeneratedMessage with Message[A]: GeneratedMessageCompanion](
    str: String
  ): A = {
    parser.fromJsonString(str)
  }

  implicit def protoToReader[T <: GeneratedMessage with Message[T]: GeneratedMessageCompanion]
    : Reads[T] =
    new Reads[T] {
      def reads(value: JsValue) =
        try {
          JsSuccess(parser.fromJson(value))
        } catch {
          case NonFatal(e) =>
            JsError(JsonValidationError(e.toString, e))
        }
    }

  implicit def protoToWriter[T <: GeneratedMessage with Message[T]]: Writes[T] = new Writes[T] {
    def writes(obj: T): JsValue = printer.toJson(obj)
  }

  @deprecated("Use parsePrimitive(protoType, value, onError) instead.", "0.9.0")
  def parsePrimitive(
    scalaType: ScalaType,
    protoType: FieldDescriptorProto.Type,
    value: JsValue,
    onError: => PValue
  ): PValue =
    parsePrimitive(protoType, value, onError)

  def parsePrimitive(
    protoType: FieldDescriptorProto.Type,
    value: JsValue,
    onError: => PValue
  ): PValue = (protoType, value) match {
    case (Type.TYPE_UINT32 | Type.TYPE_FIXED32, JsNumber(x)) =>
      parseUint32(x.toString)
    case (Type.TYPE_UINT32 | Type.TYPE_FIXED32, JsString(x)) =>
      parseUint32(x)

    case (Type.TYPE_SINT32 | Type.TYPE_INT32 | Type.TYPE_SFIXED32, JsNumber(x)) =>
      parseInt32(x.toString)
    case (Type.TYPE_SINT32 | Type.TYPE_INT32 | Type.TYPE_SFIXED32, JsString(x)) =>
      parseInt32(x)

    case (Type.TYPE_UINT64 | Type.TYPE_FIXED64, JsNumber(x)) =>
      parseUint64(x.toString)
    case (Type.TYPE_UINT64 | Type.TYPE_FIXED64, JsString(x)) =>
      parseUint64(x)

    case (Type.TYPE_SINT64 | Type.TYPE_INT64 | Type.TYPE_SFIXED64, JsNumber(x)) =>
      parseInt64(x.toString)
    case (Type.TYPE_SINT64 | Type.TYPE_INT64 | Type.TYPE_SFIXED64, JsString(x)) =>
      parseInt64(x)

    case (Type.TYPE_DOUBLE, JsNumber(x)) =>
      parseDouble(x.toString)
    case (Type.TYPE_DOUBLE, JsString(v)) =>
      parseDouble(v)
    case (Type.TYPE_FLOAT, JsNumber(x)) =>
      parseFloat(x.toString)
    case (Type.TYPE_FLOAT, JsString(v)) =>
      parseFloat(v)

    case (Type.TYPE_BOOL, JsBoolean(b)) =>
      PBoolean(b)
    case (Type.TYPE_BOOL, JsString("true")) =>
      PBoolean(true)
    case (Type.TYPE_BOOL, JsString("false")) =>
      PBoolean(false)

    case (Type.TYPE_STRING, JsString(s)) =>
      PString(s)
    case (Type.TYPE_BYTES, JsString(s)) =>
      PByteString(ByteString.copyFrom(java.util.Base64.getDecoder.decode(s)))
    case _ =>
      onError
  }
}
