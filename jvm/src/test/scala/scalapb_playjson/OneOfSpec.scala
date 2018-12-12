package scalapb_playjson

import com.google.protobuf.util.JsonFormat.{printer => ProtobufJavaPrinter}
import jsontest.oneof.OneOf._
import jsontest.oneof.{OneOf, OneOfMessage}
import play.api.libs.json.Json.parse
import org.scalatest.prop._
import org.scalatest.{FlatSpec, MustMatchers}

class OneOfSpec extends FlatSpec with MustMatchers with TableDrivenPropertyChecks {

  val examples = Table(
    ("message", "json"),
    (OneOf.defaultInstance, "{}"),
    (OneOf(Field.Empty), "{}"),
    (OneOf(Field.Primitive("")), """{"primitive":""}"""),
    (OneOf(Field.Primitive("test")), """{"primitive":"test"}"""),
    (OneOf(Field.Wrapper("")), """{"wrapper":""}"""),
    (OneOf(Field.Wrapper("test")), """{"wrapper":"test"}"""),
    (OneOf(Field.Message(OneOfMessage())), """{"message":{}}"""),
    (OneOf(Field.Message(OneOfMessage(Some("test")))), """{"message":{"field":"test"}}""")
  )

  forEvery(examples) { (message: OneOf, json: String) =>
    new Printer(includingDefaultValueFields = false).toJson(message) must be(parse(json))
    new Printer(includingDefaultValueFields = false).toJson(message) must be(
      parse(
        ProtobufJavaPrinter().print(toJavaProto(message))
      )
    )

    new Printer(includingDefaultValueFields = true).toJson(message) must be(parse(json))
    new Printer(includingDefaultValueFields = true).toJson(message) must be(
      parse(
        ProtobufJavaPrinter().includingDefaultValueFields().print(toJavaProto(message))
      )
    )
  }

}
