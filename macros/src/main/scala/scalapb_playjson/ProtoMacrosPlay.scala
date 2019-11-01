package scalapb_playjson

import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}

import scala.reflect.macros.blackbox
import scala.language.experimental.macros
import scala.util.Try
import play.api.libs.json.Json

object ProtoMacrosPlay {
  implicit class ProtoContextPlay(private val c: StringContext) extends AnyVal {
    def struct(): com.google.protobuf.struct.Struct =
      macro ProtoMacrosPlay.protoStructInterpolation
    def value(): com.google.protobuf.struct.Value =
      macro ProtoMacrosPlay.protoValueInterpolation
  }

  implicit class FromJsonPlay[A <: GeneratedMessage with Message[A]](
    private val companion: GeneratedMessageCompanion[A]
  ) extends AnyVal {
    def fromJsonConstant(json: String): A =
      macro ProtoMacrosPlay.fromJsonConstantImpl0[A]

    def fromJson(json: String): A =
      macro ProtoMacrosPlay.fromJsonImpl[A]

    def fromJsonDebug(json: String): A =
      macro ProtoMacrosPlay.fromJsonDebugImpl

    def fromJsonOpt(json: String): Option[A] =
      macro ProtoMacrosPlay.fromJsonOptImpl[A]

    def fromJsonEither(json: String): Either[Throwable, A] =
      macro ProtoMacrosPlay.fromJsonEitherImpl[A]

    def fromJsonTry(json: String): Try[A] =
      macro ProtoMacrosPlay.fromJsonTryImpl[A]
  }
}

class ProtoMacrosPlay(override val c: blackbox.Context) extends scalapb_json.ProtoMacrosCommon(c) {
  import c.universe._

  override def fromJsonImpl[A: c.WeakTypeTag](json: c.Tree): c.Tree = {
    val A = weakTypeTag[A]
    q"_root_.scalapb_playjson.JsonFormat.fromJsonString[$A]($json)"
  }

  override def fromJsonConstantImpl[A <: GeneratedMessage with Message[A]: c.WeakTypeTag: GeneratedMessageCompanion](
    string: String
  ): c.Tree = {
    val A = weakTypeTag[A]
    scalapb_playjson.JsonFormat.fromJsonString[A](string)
    q"_root_.scalapb_playjson.JsonFormat.fromJsonString[$A]($string)"
  }

  override protected[this] def protoString2Struct(string: String): c.Tree = {
    val json = Json.parse(string)
    val struct = StructFormat.structParser(json)
    q"$struct"
  }

  override protected[this] def protoString2Value(string: String): c.Tree = {
    val json = Json.parse(string)
    val value = StructFormat.structValueParser(json)
    q"$value"
  }
}
