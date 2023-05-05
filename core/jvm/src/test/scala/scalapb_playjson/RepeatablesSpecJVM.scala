package scalapb_playjson

import scalapb.{GeneratedMessageCompanion, JavaProtoSupport, UnknownFieldSet}
import scalapb.e2e.repeatables.RepeatablesTest
import scalaprops.Gen
import scalaprops.Scalaprops
import scalaprops.Property.forAll
import com.google.protobuf.ByteString

object RepeatablesSpecJVM extends Scalaprops {
  private[this] implicit val byteStringGen: Gen[ByteString] =
    Gen.alphaNumString.map(ByteString.copyFromUtf8)

  val g = new RepeatableTestGen({
    import scalaprops.ScalapropsShapeless._
    import RepeatableTestGen.Base._
    Gen[UnknownFieldSet]
  })
  import g._
  val `UnknownFieldSet same as java` = {
    val javaPrinter = com.google.protobuf.util.JsonFormat.printer()
    val companion = implicitly[GeneratedMessageCompanion[RepeatablesTest]]
      .asInstanceOf[JavaProtoSupport[RepeatablesTest, com.google.protobuf.GeneratedMessageV3]]
    forAll { (v: RepeatablesTest) =>
      val scalaJson = JsonFormat.printer.print(v)
      val javaJson = javaPrinter.print(companion.toJavaProto(v))
      import play.api.libs.json.Json.parse
      parse(scalaJson) == parse(javaJson)
    }
  }
}
