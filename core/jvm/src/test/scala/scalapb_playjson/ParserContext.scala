package scalapb_playjson

import com.google.protobuf.util.JsonFormat.{Parser => JavaJsonParser}
import com.google.protobuf.util.{JsonFormat => JavaJsonFormat}

case class ParserContext(scalaParser: Parser, javaParser: JavaJsonParser)

class DefaultParserContext {
  implicit val pc: ParserContext =
    ParserContext(new Parser(), JavaJsonFormat.parser())
}

class IgnoringUnknownParserContext {
  implicit val pc: ParserContext = ParserContext(
    new Parser().ignoringUnknownFields,
    JavaJsonFormat.parser.ignoringUnknownFields()
  )
}
