package generators.`type`

import java.util

import generators.`type`.EnumTypeGenerators.DefaultEnumTypeGenerator
import generators.`type`.IntegerTypeGenerators.DefaultIntegerTypeGenerator
import generators.`type`.StringTypeGenerators.DefaultStringTypeGenerator
import org.scalacheck.Gen

import scala.collection.JavaConverters._

object ListTypeGenerators {

  object DefaultEnumListTypeGenerator {
    def apply(): (Int, Array[Any]) => Gen[util.List[Any]] = (numOfElem: Int, enumValues: Array[Any]) =>
      Gen.listOfN(numOfElem, DefaultEnumTypeGenerator()(enumValues)).map(_.asJava)
  }

  object DefaultStringListTypeGenerator {
    def apply(): Int => Gen[util.List[String]] = (numOfElem: Int) =>
      Gen.listOfN(numOfElem, DefaultStringTypeGenerator()).map(_.asJava)
  }

  object DefaultIntegerListTypeGenerator {
    def apply(): Int => Gen[util.List[java.lang.Integer]] = (numOfElem: Int) =>
      Gen.listOfN(numOfElem, DefaultIntegerTypeGenerator()).map(_.asJava)
  }
}
