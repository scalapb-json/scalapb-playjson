package scalapb_playjson

import com.google.protobuf.{GeneratedMessageV3, InvalidProtocolBufferException}
import com.google.protobuf.util.JsonFormat.{TypeRegistry => JavaTypeRegistry}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, JavaProtoSupport, Message}
import org.scalatest.Assertion
import scalapb_json.JsonFormatException
import org.scalatest.matchers.must.Matchers

trait JavaAssertionsPlatform {
  self: Matchers with JavaAssertions =>

  def registeredCompanions: Seq[GeneratedMessageCompanion[_]]

  val JavaJsonTypeRegistry =
    registeredCompanions.foldLeft(JavaTypeRegistry.newBuilder())(_ add _.javaDescriptor).build()
  val JavaJsonPrinter =
    com.google.protobuf.util.JsonFormat.printer().usingTypeRegistry(JavaJsonTypeRegistry)
  val JavaJsonParser = com.google.protobuf.util.JsonFormat.parser()

  def assertJsonIsSameAsJava[T <: GeneratedMessage with Message[T]](
    v: T,
    checkRoundtrip: Boolean = true
  )(implicit cmp: GeneratedMessageCompanion[T]) = {
    val scalaJson = ScalaJsonPrinter.print(v)
    val javaJson = JavaJsonPrinter.print(
      cmp.asInstanceOf[JavaProtoSupport[T, com.google.protobuf.GeneratedMessageV3]].toJavaProto(v)
    )

    import play.api.libs.json.Json.parse
    parse(scalaJson) must be(parse(javaJson))
    if (checkRoundtrip) {
      ScalaJsonParser.fromJsonString[T](scalaJson) must be(v)
    }
  }

  def javaParse[T <: com.google.protobuf.GeneratedMessageV3.Builder[T]](
    json: String,
    b: com.google.protobuf.GeneratedMessageV3.Builder[T]
  ) = {
    JavaJsonParser.merge(json, b)
    b.build()
  }

  def assertParse[T <: scalapb.GeneratedMessage with scalapb.Message[T], J <: GeneratedMessageV3](
    json: String,
    expected: T
  )(implicit
    cmp: GeneratedMessageCompanion[T] with JavaProtoSupport[T, J],
    parserContext: ParserContext
  ): Assertion = {
    val parsedJava: J = {
      val builder = cmp.toJavaProto(cmp.defaultInstance).newBuilderForType()
      parserContext.javaParser.merge(json, builder)
      builder.build().asInstanceOf[J]
    }

    val parsedScala = parserContext.scalaParser.fromJsonString[T](json)(cmp)
    parsedScala must be(expected)
    cmp.fromJavaProto(parsedJava) must be(expected)
  }

  def assertFails[T <: scalapb.GeneratedMessage with scalapb.Message[T], J <: GeneratedMessageV3](
    json: String,
    cmp: GeneratedMessageCompanion[T] with JavaProtoSupport[T, J]
  )(implicit parserContext: ParserContext): Assertion = {
    val builder = cmp.toJavaProto(cmp.defaultInstance).newBuilderForType()
    assertThrows[InvalidProtocolBufferException] {
      parserContext.javaParser.merge(json, builder)
    }
    assertThrows[JsonFormatException] {
      parserContext.scalaParser.fromJsonString[T](json)(cmp)
    }
  }
}
