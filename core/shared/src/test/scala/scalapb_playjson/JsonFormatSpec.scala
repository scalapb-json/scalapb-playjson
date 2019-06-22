package scalapb_playjson

import java.math.BigInteger

import play.api.libs.json._
import play.api.libs.json.Json.parse
import org.scalatest.{Assertion, FlatSpec, MustMatchers, OptionValues}
import jsontest.test._
import jsontest.test3._
import com.google.protobuf.any.{Any => PBAny}
import com.google.protobuf.field_mask.FieldMask
import jsontest.custom_collection.{Guitar, Studio}
import scalapb_json._

class JsonFormatSpec extends FlatSpec with MustMatchers with OptionValues with JsonFormatSpecBase {

  val TestProto = MyTest().update(
    _.hello := "Foo",
    _.foobar := 37,
    _.primitiveSequence := Seq("a", "b", "c"),
    _.repMessage := Seq(MyTest(), MyTest(hello = Some("h11"))),
    _.optMessage := MyTest().update(_.foobar := 39),
    _.stringToInt32 := Map("foo" -> 14, "bar" -> 19),
    _.intToMytest := Map(14 -> MyTest(), 35 -> MyTest(hello = Some("boo"))),
    _.repEnum := Seq(MyEnum.V1, MyEnum.V2, MyEnum.UNKNOWN),
    _.optEnum := MyEnum.V2,
    _.intToEnum := Map(32 -> MyEnum.V1, 35 -> MyEnum.V2),
    _.stringToBool := Map("ff" -> false, "tt" -> true),
    _.boolToString := Map(false -> "ff", true -> "tt"),
    _.optBool := false
  )

  val TestJson =
    """{
      |  "hello": "Foo",
      |  "foobar": 37,
      |  "primitiveSequence": ["a", "b", "c"],
      |  "repMessage": [{}, {"hello": "h11"}],
      |  "optMessage": {"foobar": 39},
      |  "stringToInt32": {"foo": 14, "bar": 19},
      |  "intToMytest": {"14": {}, "35": {"hello": "boo"}},
      |  "repEnum": ["V1", "V2", "UNKNOWN"],
      |  "optEnum": "V2",
      |  "intToEnum": {"32": "V1", "35": "V2"},
      |  "stringToBool": {"ff": false, "tt": true},
      |  "boolToString": {"false": "ff", "true": "tt"},
      |  "optBool": false
      |}
      |""".stripMargin

  val TestJsonWithType =
    """{
      |  "@type": "type.googleapis.com/jsontest.MyTest",
      |  "hello": "Foo",
      |  "foobar": 37,
      |  "primitiveSequence": ["a", "b", "c"],
      |  "repMessage": [{}, {"hello": "h11"}],
      |  "optMessage": {"foobar": 39},
      |  "stringToInt32": {"foo": 14, "bar": 19},
      |  "intToMytest": {"14": {}, "35": {"hello": "boo"}},
      |  "repEnum": ["V1", "V2", "UNKNOWN"],
      |  "optEnum": "V2",
      |  "intToEnum": {"32": "V1", "35": "V2"},
      |  "stringToBool": {"ff": false, "tt": true},
      |  "boolToString": {"false": "ff", "true": "tt"},
      |  "optBool": false
      |}
      |""".stripMargin

  val DefaultTestJson =
    """{
      |  "hello": "",
      |  "foobar": 0,
      |  "bazinga": 0,
      |  "primitiveSequence": [],
      |  "repMessage": [],
      |  "stringToInt32": {},
      |  "intToMytest": {},
      |  "repEnum": [],
      |  "optEnum": "UNKNOWN",
      |  "intToEnum": {},
      |  "boolToString": {},
      |  "stringToBool": {},
      |  "optBool": false
      |}""".stripMargin

  val PreservedTestJson =
    """{
      |  "hello": "Foo",
      |  "foobar": 37,
      |  "primitive_sequence": ["a", "b", "c"],
      |  "rep_message": [{}, {"hello": "h11"}],
      |  "opt_message": {"foobar": 39},
      |  "string_to_int32": {"foo": 14, "bar": 19},
      |  "int_to_mytest": {"14": {}, "35": {"hello": "boo"}},
      |  "rep_enum": ["V1", "V2", "UNKNOWN"],
      |  "opt_enum": "V2",
      |  "int_to_enum": {"32": "V1", "35": "V2"},
      |  "string_to_bool": {"ff": false, "tt": true},
      |  "bool_to_string": {"false": "ff", "true": "tt"},
      |  "opt_bool": false
      |}
      |""".stripMargin

  "Empty object" should "give empty json" in {
    JsonFormat.toJson(MyTest()) must be(Json.obj())
  }

  "Empty object" should "give empty json for MyTest3" in {
    JsonFormat.toJson(MyTest3()) must be(Json.obj())
  }

  "Zero maps" should "give correct json" in {
    JsonFormat.toJson(
      MyTest(
        stringToInt32 = Map("" -> 17),
        intToMytest = Map(0 -> MyTest()),
        fixed64ToBytes = Map(0L -> com.google.protobuf.ByteString.copyFromUtf8("foobar"))
      )
    ) must be(parse("""|{
                 |  "stringToInt32": {"": 17},
                 |  "intToMytest": {"0": {}},
                 |  "fixed64ToBytes": {"0": "Zm9vYmFy"}
                 |}""".stripMargin))
  }

  "Zero maps" should "give correct json for MyTest3" in {
    JsonFormat.toJson(
      MyTest3(
        stringToInt32 = Map("" -> 17),
        intToMytest = Map(0 -> MyTest()),
        fixed64ToBytes = Map(0L -> com.google.protobuf.ByteString.copyFromUtf8("foobar"))
      )
    ) must be(parse("""|{
                 |  "stringToInt32": {"": 17},
                 |  "intToMytest": {"0": {}},
                 |  "fixed64ToBytes": {"0": "Zm9vYmFy"}
                 |}""".stripMargin))
  }

  "Set treat" should "give correct json" in {
    JsonFormat.toJson(MyTest(trickOrTreat = MyTest.TrickOrTreat.Treat(MyTest()))) must be(
      parse("""{"treat": {}}""")
    )
  }

  "Parse treat" should "give correct proto with proto2" in {
    JsonFormat.fromJsonString[MyTest]("""{"treat": {"hello": "x"}}""") must be(
      MyTest(trickOrTreat = MyTest.TrickOrTreat.Treat(MyTest(hello = Some("x"))))
    )
    JsonFormat.fromJsonString[MyTest]("""{"treat": {}}""") must be(
      MyTest(trickOrTreat = MyTest.TrickOrTreat.Treat(MyTest()))
    )
  }

  "Parse treat" should "give correct proto with proto3" in {
    JsonFormat.fromJsonString[MyTest3]("""{"treat": {"s": "x"}}""") must be(
      MyTest3(trickOrTreat = MyTest3.TrickOrTreat.Treat(MyTest3(s = "x")))
    )
    JsonFormat.fromJsonString[MyTest3]("""{"treat": {}}""") must be(
      MyTest3(trickOrTreat = MyTest3.TrickOrTreat.Treat(MyTest3()))
    )
  }

  "JsonFormat" should "encode and decode enums for proto3" in {
    val v1Value = MyTest3(optEnum = MyTest3.MyEnum3.V1)
    assert(JsonFormat.toJson(v1Value) === parse("""{"optEnum": "V1"}"""))
    val defaultValue = MyTest3(optEnum = MyTest3.MyEnum3.UNKNOWN)
    assert(JsonFormat.toJson(defaultValue) === parse("""{}"""))
    JsonFormat.fromJsonString[MyTest3](JsonFormat.toJsonString(v1Value)) must be(v1Value)
    JsonFormat.fromJsonString[MyTest3](JsonFormat.toJsonString(defaultValue)) must be(defaultValue)
  }

  "parsing one offs" should "work correctly for issue 315" in {
    JsonFormat.fromJsonString[jsontest.issue315.Msg]("""
    {
          "baz" : "1",
          "foo" : {
            "cols" : "1"
          }
    }""") must be(
      jsontest.issue315.Msg(
        baz = "1",
        someUnion = jsontest.issue315.Msg.SomeUnion.Foo(jsontest.issue315.Foo(cols = "1"))
      )
    )
  }

  "parsing null" should "give default value" in {
    JsonFormat.fromJsonString[jsontest.test.MyTest]("""
    {
          "optMessage" : null,
          "optBool": null,
          "optEnum": null,
          "repEnum": null
    }""") must be(jsontest.test.MyTest())
  }

  "TestProto" should "be TestJson when converted to Proto" in {
    JsonFormat.toJson(TestProto) must be(parse(TestJson))
  }

  "TestJson" should "be TestProto when parsed from json" in {
    JsonFormat.fromJsonString[MyTest](TestJson) must be(TestProto)
  }

  "Empty object" should "give full json if including default values" in {
    new Printer(includingDefaultValueFields = true).toJson(MyTest()) must be(
      parse("""{
          |  "hello": "",
          |  "foobar": 0,
          |  "bazinga": "0",
          |  "primitiveSequence": [],
          |  "repMessage": [],
          |  "stringToInt32": {},
          |  "intToMytest": {},
          |  "repEnum": [],
          |  "optEnum": "UNKNOWN",
          |  "intToEnum": {},
          |  "boolToString": {},
          |  "stringToBool": {},
          |  "optBs": "",
          |  "optBool": false,
          |  "fixed64ToBytes": {}
          |}""".stripMargin)
    )
  }

  "Empty object" should "with preserve field names should work" in {
    new Printer(includingDefaultValueFields = true, preservingProtoFieldNames = true)
      .toJson(MyTest()) must be(
      parse("""{
          |  "hello": "",
          |  "foobar": 0,
          |  "bazinga": "0",
          |  "primitive_sequence": [],
          |  "rep_message": [],
          |  "string_to_int32": {},
          |  "int_to_mytest": {},
          |  "rep_enum": [],
          |  "opt_enum": "UNKNOWN",
          |  "int_to_enum": {},
          |  "bool_to_string": {},
          |  "string_to_bool": {},
          |  "opt_bs": "",
          |  "opt_bool": false,
          |  "fixed64_to_bytes": {}
          |}""".stripMargin)
    )
  }

  "TestProto" should "format int64 as JSON string" in {
    new Printer().print(MyTest(bazinga = Some(642))) must be("""{"bazinga":"642"}""")
  }

  "TestProto" should "format int64 as JSON number" in {
    new Printer(formattingLongAsNumber = true).print(MyTest(bazinga = Some(642))) must be(
      """{"bazinga":642}"""
    )
  }

  "TestProto" should "parse numbers formatted as JSON string" in {
    val parser = new Parser()
    def validateAccepts(json: String, expected: IntFields): Assertion = {
      parser.fromJsonString[IntFields](json) must be(expected)
    }
    def validateRejects(json: String): Assertion = {
      a[JsonFormatException] mustBe thrownBy { parser.fromJsonString[IntFields](json) }
    }

    // int32
    validateAccepts("""{"int":"642"}""", IntFields(int = Some(642)))
    validateAccepts("""{"int":"-1"}""", IntFields(int = Some(-1)))
    validateAccepts(s"""{"int":"${Integer.MAX_VALUE}"}""", IntFields(int = Some(Integer.MAX_VALUE)))
    validateAccepts(s"""{"int":"${Integer.MIN_VALUE}"}""", IntFields(int = Some(Integer.MIN_VALUE)))
    validateRejects(s"""{"int":"${Integer.MAX_VALUE.toLong + 1}"}""")
    validateRejects(s"""{"int":"${Integer.MIN_VALUE.toLong - 1}"}""")

    // int64
    validateAccepts("""{"long":"642"}""", IntFields(long = Some(642L)))
    validateAccepts("""{"long":"-1"}""", IntFields(long = Some(-1L)))
    validateAccepts(s"""{"long":"${Long.MaxValue}"}""", IntFields(long = Some(Long.MaxValue)))
    validateAccepts(s"""{"long":"${Long.MinValue}"}""", IntFields(long = Some(Long.MinValue)))
    validateRejects(s"""{"long":"${BigInt(Long.MaxValue) + 1}"}""")
    validateRejects(s"""{"long":"${BigInt(Long.MinValue) - 1}"}""")

    // uint32
    val uint32max: Long = (1L << 32) - 1
    validateAccepts(s"""{"uint":"$uint32max"}""", IntFields(uint = Some(uint32max.toInt)))
    validateRejects(s"""{"uint":"${uint32max + 1}"}""")
    validateRejects("""{"uint":"-1"}""")

    // uint64
    val uint64max: BigInt = (BigInt(1) << 64) - 1
    validateAccepts(s"""{"ulong":"$uint64max"}""", IntFields(ulong = Some(uint64max.toLong)))
    validateRejects(s"""{"ulong":"${uint64max + 1}"}""")
    validateRejects("""{"ulong":"-1"}""")

    // sint32
    validateAccepts(
      s"""{"sint":"${Integer.MAX_VALUE}"}""",
      IntFields(sint = Some(Integer.MAX_VALUE))
    )
    validateAccepts(
      s"""{"sint":"${Integer.MIN_VALUE}"}""",
      IntFields(sint = Some(Integer.MIN_VALUE))
    )
    validateRejects(s"""{"sint":"${Integer.MAX_VALUE.toLong + 1}"}""")
    validateRejects(s"""{"sint":"${Integer.MIN_VALUE.toLong - 1}"}""")

    // sint64
    validateAccepts(s"""{"slong":"${Long.MaxValue}"}""", IntFields(slong = Some(Long.MaxValue)))
    validateAccepts(s"""{"slong":"${Long.MinValue}"}""", IntFields(slong = Some(Long.MinValue)))
    validateRejects(s"""{"slong":"${BigInt(Long.MaxValue) + 1}"}""")
    validateRejects(s"""{"slong":"${BigInt(Long.MinValue) - 1}"}""")

    // fixed32
    validateAccepts(s"""{"fixint":"$uint32max"}""", IntFields(fixint = Some(uint32max.toInt)))
    validateRejects(s"""{"fixint":"${uint32max + 1}"}""")
    validateRejects("""{"fixint":"-1"}""")

    // fixed64
    validateAccepts(s"""{"fixlong":"$uint64max"}""", IntFields(fixlong = Some(uint64max.toLong)))
    validateRejects(s"""{"fixlong":"${uint64max + 1}"}""")
    validateRejects("""{"fixlong":"-1"}""")

  }

  "TestProto" should "produce valid JSON output for unsigned integers" in {
    val uint32max: Long = (1L << 32) - 1
    JsonFormat.toJson(IntFields(uint = Some(uint32max.toInt))) must be(
      parse(s"""{"uint":$uint32max}""")
    )
    JsonFormat.toJson(IntFields(uint = Some(1))) must be(parse(s"""{"uint":1}"""))
    JsonFormat.toJson(IntFields(fixint = Some(uint32max.toInt))) must be(
      parse(s"""{"fixint":$uint32max}""")
    )
    JsonFormat.toJson(IntFields(fixint = Some(1))) must be(parse(s"""{"fixint":1}"""))
    val uint64max: BigInt = (BigInt(1) << 64) - 1
    JsonFormat.toJson(IntFields(ulong = Some(uint64max.toLong))) must be(
      parse(s"""{"ulong":"$uint64max"}""")
    )
    JsonFormat.toJson(IntFields(ulong = Some(1))) must be(parse(s"""{"ulong":"1"}"""))
    JsonFormat.toJson(IntFields(fixlong = Some(uint64max.toLong))) must be(
      parse(s"""{"fixlong":"$uint64max"}""")
    )
    JsonFormat.toJson(IntFields(fixlong = Some(1))) must be(parse(s"""{"fixlong":"1"}"""))
  }

  "TestProto" should "parse an enum formatted as number" in {
    new Parser().fromJsonString[MyTest]("""{"optEnum":1}""") must be(
      MyTest(optEnum = Some(MyEnum.V1))
    )
    new Parser().fromJsonString[MyTest]("""{"optEnum":2}""") must be(
      MyTest(optEnum = Some(MyEnum.V2))
    )
  }

  "PreservedTestJson" should "be TestProto when parsed from json" in {
    new Parser(preservingProtoFieldNames = true).fromJsonString[MyTest](PreservedTestJson) must be(
      TestProto
    )
  }

  "TestAllTypesProto" should "parse NaNs" in {
    val i = s"""{
      "optionalDouble": "NaN",
      "optionalFloat": "NaN"
    }"""
    val out = JsonFormat.fromJsonString[TestAllTypes](i)
    out.optionalDouble.value.isNaN must be(true)
    out.optionalFloat.value.isNaN must be(true)
    (JsonFormat.toJson(out) \ "optionalDouble") must be(JsDefined(JsString("NaN")))
    (JsonFormat.toJson(out) \ "optionalFloat") must be(JsDefined(JsString("NaN")))
  }

  "TestAllTypesProto" should "parse Infinity" in {
    val i = s"""{
      "optionalDouble": "Infinity",
      "optionalFloat": "Infinity"
    }"""
    val out = JsonFormat.fromJsonString[TestAllTypes](i)
    out.optionalDouble.value.isPosInfinity must be(true)
    out.optionalFloat.value.isPosInfinity must be(true)
  }

  "TestAllTypesProto" should "parse -Infinity" in {
    val i = s"""{
      "optionalDouble": "-Infinity",
      "optionalFloat": "-Infinity"
    }"""
    val out = JsonFormat.fromJsonString[TestAllTypes](i)
    out.optionalDouble.value.isNegInfinity must be(true)
    out.optionalFloat.value.isNegInfinity must be(true)
    (JsonFormat.toJson(out) \ "optionalDouble") must be(JsDefined(JsString("-Infinity")))
    (JsonFormat.toJson(out) \ "optionalFloat") must be(JsDefined(JsString("-Infinity")))
  }

  "TestAllTypesProto" should "take strings" in {
    val i = s"""{
      "optionalDouble": "1.4",
      "optionalFloat": "1.4"
    }"""
    val out = JsonFormat.fromJsonString[TestAllTypes](i)
    out.optionalDouble.value must be(1.4)
    out.optionalFloat.value must be(1.4f)
    (JsonFormat.toJson(out) \ "optionalDouble") must be(JsDefined(JsNumber(1.4)))
    val x = (JsonFormat.toJson(out) \ "optionalFloat").validate[JsNumber].get

    (x.value - 1.4).abs must be <= BigDecimal("0.001")
  }

  "parser" should "reject out of range numeric values" in {
    assertAccepts("optionalInt32", String.valueOf(Integer.MAX_VALUE))
    assertAccepts("optionalInt32", String.valueOf(Integer.MIN_VALUE))
    assertRejects("optionalInt32", String.valueOf(Integer.MAX_VALUE + 1L))
    assertRejects("optionalInt32", String.valueOf(Integer.MIN_VALUE - 1L))

    assertAccepts("optionalUint32", String.valueOf(Integer.MAX_VALUE + 1L))
    assertRejects("optionalUint32", "123456789012345")
    assertRejects("optionalUint32", "-1")

    val one = new BigInteger("1")
    val maxLong = new BigInteger(String.valueOf(Long.MaxValue))
    val minLong = new BigInteger(String.valueOf(Long.MinValue))
    assertAcceptsQuotes("optionalInt64", maxLong.toString)
    assertAcceptsQuotes("optionalInt64", minLong.toString)
    assertRejects("optionalInt64", maxLong.add(one).toString)
    assertRejects("optionalInt64", minLong.subtract(one).toString)

    assertAccepts("optionalUint64", maxLong.add(one).toString)
    assertRejects("optionalUint64", "1234567890123456789012345")
    assertRejects("optionalUint64", "-1")

    assertAccepts("optionalBool", "true")
    assertRejects("optionalBool", "1")
    assertRejects("optionalBool", "0")

    assertAccepts("optionalFloat", String.valueOf(Float.MaxValue))
    assertAccepts("optionalFloat", String.valueOf(-Float.MaxValue))
    assertRejects("optionalFloat", String.valueOf(Double.MaxValue))
    assertRejects("optionalFloat", String.valueOf(-Double.MaxValue))

    val maxDouble = new java.math.BigDecimal(Double.MaxValue)
    assertAccepts("optionalDouble", maxDouble.toString)
  }

  val anyEnabledTypeRegistry = TypeRegistry.empty.addMessageByCompanion(TestProto.companion)
  val anyEnabledParser = new Parser(typeRegistry = anyEnabledTypeRegistry)
  val anyEnabledPrinter = new Printer(typeRegistry = anyEnabledTypeRegistry)

  "TestProto packed as any" should "give TestJsonWithType after JSON serialization" in {
    val any = PBAny.pack(TestProto)

    anyEnabledPrinter.toJson(any) must be(parse(TestJsonWithType))
  }

  "TestJsonWithType" should "be TestProto packed as any when parsed from JSON" in {
    val out = anyEnabledParser.fromJson[PBAny](parse(TestJsonWithType))
    out.unpack[MyTest] must be(TestProto)
  }

  "toJsonString" should "generate correct JSON for messages with custom collection type" in {
    val studio = Studio().addGuitars(Guitar(numberOfStrings = 12))
    val expectedStudioJsonString = """{"guitars":[{"numberOfStrings":12}]}"""
    val studioJsonString = JsonFormat.toJsonString(studio)
    studioJsonString must be(expectedStudioJsonString)
  }

  "fromJsonString" should "parse JSON correctly to message with custom collection type" in {
    val expectedStudio = Studio().addGuitars(Guitar(numberOfStrings = 12))
    val studioJsonString = """{"guitars":[{"numberOfStrings":12}]}"""
    import Studio.messageCompanion
    val studio = JsonFormat.fromJsonString(studioJsonString)
    studio must be(expectedStudio)
  }

  "formatEnumAsNumber" should "format enums as number" in {
    val p = MyTest().update(_.optEnum := MyEnum.V2)
    new Printer(formattingEnumsAsNumber = true).toJson(p) must be(parse(s"""{"optEnum":2}"""))
  }

  "FieldMask" should "parse and write" in {
    // https://github.com/google/protobuf/blob/47b7d2c7ca/java/util/src/test/java/com/google/protobuf/util/JsonFormatTest.java#L761-L770
    val message = TestFieldMask(Some(FieldMask(Seq("foo.bar", "baz", "foo_bar.baz"))))
    val json = """{"fieldMaskValue":"foo.bar,baz,fooBar.baz"}"""
    assert(JsonFormat.toJsonString(message) == json)
    assert(JsonFormat.fromJsonString[TestFieldMask](json) == message)
  }
}
