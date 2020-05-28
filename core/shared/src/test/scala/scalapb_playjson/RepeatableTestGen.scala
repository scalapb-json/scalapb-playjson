package scalapb_playjson

import scalapb.UnknownFieldSet
import scalapb.e2e.repeatables.RepeatablesTest
import scalapb.e2e.repeatables.RepeatablesTest.Nested
import scalaprops.Gen

object RepeatableTestGen {
  object Base {
    implicit val doubleGen: Gen[Double] =
      Gen.genFiniteDouble

    implicit val stringGen: Gen[String] =
      Gen.alphaNumString

    implicit def seqGen[A: Gen]: Gen[Seq[A]] =
      Gen(Gen[Vector[A]].f)
  }
}

class RepeatableTestGen(unknown: Gen[UnknownFieldSet]) {
  import RepeatableTestGen.Base._

  private[this] implicit val unknownFieldSetGen: Gen[UnknownFieldSet] =
    unknown

  private[this] implicit val nestedGen: Gen[Nested] =
    Gen.from1(Nested.of)

  private[this] implicit val enumGen: Gen[RepeatablesTest.Enum] =
    Gen.elements(RepeatablesTest.Enum.values.head, RepeatablesTest.Enum.values.tail: _*)

  implicit val repGen: Gen[RepeatablesTest] =
    Gen.from6(RepeatablesTest.of)
}
