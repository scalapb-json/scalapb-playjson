package scalapb_playjson

import scalapb.UnknownFieldSet
import scalapb.e2e.repeatables.RepeatablesTest
import scalapb.e2e.repeatables.RepeatablesTest.Nested
import scalaprops.Gen
import scalaprops.Scalaprops
import scalaprops.Property.forAll

object RepeatablesSpec extends Scalaprops {
  val g = new RepeatableTestGen(
    Gen.value(UnknownFieldSet.empty)
  )
  import g._

  val `fromJson invert toJson single` = forAll {
    val rep = RepeatablesTest(
      strings = Seq("s1", "s2"),
      ints = Seq(14, 19),
      doubles = Seq(3.14, 2.17),
      nesteds = Seq(Nested())
    )
    val j = JsonFormat.toJson(rep)
    JsonFormat.fromJson[RepeatablesTest](j) == rep
  }

  val `fromJson invert toJson` = forAll { rep: RepeatablesTest =>
    val j = JsonFormat.toJson(rep)
    JsonFormat.fromJson[RepeatablesTest](j) == rep
  }
}
