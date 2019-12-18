package scalapb_playjson

import scalapb_json.JsonFormatException
import jsontest.test._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

trait JsonFormatSpecBase extends JavaAssertions { self: AnyFlatSpec with Matchers =>

  def assertAcceptsQuotes(field: String, value: String): Unit = {
    JsonFormat.fromJsonString[TestAllTypes](s"""{"$field": "$value"}""")
  }
  def assertAcceptsNoQuotes(field: String, value: String): Unit = {
    JsonFormat.fromJsonString[TestAllTypes](s"""{"$field": $value}""")
  }
  def assertAccepts(field: String, value: String): Unit = {
    assertAcceptsQuotes(field, value)
    assertAcceptsNoQuotes(field, value)
  }
  def assertRejectsNoQuotes(field: String, value: String): Unit = {
    assertThrows[JsonFormatException] {
      JsonFormat.fromJsonString[TestAllTypes](s"""{"$field": $value}""")
    }
  }
  def assertRejectsQuotes(field: String, value: String): Unit = {
    assertThrows[JsonFormatException] {
      JsonFormat.fromJsonString[TestAllTypes](s"""{"$field": "$value"}""")
    }
  }
  def assertRejects(field: String, value: String): Unit = {
    assertRejectsNoQuotes(field, value)
    assertRejectsQuotes(field, value)
  }
}
