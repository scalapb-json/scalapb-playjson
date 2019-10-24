package scalapb_playjson

import play.api.libs.json.JsValue

final case class EnumFormatter[T](
  writer: (Printer, T) => JsValue,
  parser: (Parser, JsValue) => Option[T]
)
