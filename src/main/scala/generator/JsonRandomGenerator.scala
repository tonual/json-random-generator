package generator

import java.util

import org.scalacheck.Gen
import utils.ReflectionUtils._
import scala.collection.JavaConverters._

import scala.reflect.runtime.universe._

class JsonRandomGenerator(strGen: Gen[String],
                          intGen: Gen[java.lang.Integer],
                          doubleGen: Gen[java.lang.Double],
                          booleanGen: Gen[java.lang.Boolean],
                          enumGen: Array[Any] => Gen[Any],
                          mapGen: Gen[Map[String, String]],
                          enumListGen: (Int, Array[Any]) => Gen[util.List[Any]],
                          strListGen: Gen[util.List[String]],
                          intListGen: Gen[util.List[java.lang.Integer]],
                          doubleListGen: Gen[util.List[java.lang.Double]],
                          booleanListGen: Gen[util.List[java.lang.Boolean]]) {
  def generate[A](topLevelObj: A)(implicit c: TypeTag[A]): A = {

    val runtimeM = runtimeMirror(getClass.getClassLoader)

    def loop(obj: Any, tp: Type): A = {
      val members = tp.decls.filter(_.isPublic)
      members.foreach { m =>
        if (m.isMethod) {
          val methodFullName = m.asMethod.fullName
          val methodName = m.asMethod.name
          val methodReturnType = m.asMethod.returnType
          val methodTypeArgs = methodReturnType.typeArgs

          if (methodFullName.startsWith("output") &&
            methodFullName.contains("get")) {
            //println(s"$methodName, $methodReturnType")

            val returnTypeSymbolFullName = resolveTypeSymbolFullName(methodReturnType)

            try {
              Class.forName(returnTypeSymbolFullName) match {
                case t if t == classOf[Object] =>
                  //Do nothing
                case t if t == classOf[String] =>
                  invokeMethod(obj, Seq(strGen.sample.get),
                    methodName.toString.replaceFirst("g", "s"), Seq(t))
                case t if t == classOf[java.lang.Integer] =>
                  invokeMethod(obj, Seq(intGen.sample.get),
                    methodName.toString.replaceFirst("g", "s"), Seq(t))
                case t if t == classOf[java.lang.Double] =>
                  invokeMethod(obj, Seq(doubleGen.sample.get),
                    methodName.toString.replaceFirst("g", "s"), Seq(t))
                case t if t == classOf[java.lang.Boolean] =>
                  invokeMethod(obj, Seq(booleanGen.sample.get),
                    methodName.toString.replaceFirst("g", "s"), Seq(t))
                case t if t.isEnum =>
                  val enumValues = getEnumValues(t.asInstanceOf[Class[Any]])
                  invokeMethod(obj, Seq(enumGen(enumValues).sample.get.asInstanceOf[AnyRef]),
                    methodName.toString.replaceFirst("g", "s"), Seq(t))
                case t if t == classOf[java.util.List[_]] =>
                  generateLists(methodTypeArgs, runtimeM, obj, methodName.toString, loop, isNested = false)
                case t if t == classOf[java.util.Map[_, _]] && methodTypeArgs.map(_.typeSymbol).map(_.asClass).map(runtimeM.runtimeClass) == List(classOf[String], classOf[String]) =>
                  val generatedMap = mapGen.sample.get
                  generatedMap.foreach {
                    case (k, v) => invokeMethod(obj, Seq(k, v),
                      "setAdditionalProperty", Seq(classOf[String], classOf[String]))
                  }
                case t if t == classOf[java.util.Map[_, _]] && methodTypeArgs.map(_.typeSymbol).map(_.asClass).map(runtimeM.runtimeClass) == List(classOf[String], classOf[Object]) =>
                  //Do nothing
                case t =>
                  val newInstance = Class.forName(returnTypeSymbolFullName).newInstance()
                  invokeMethod(obj, Seq(newInstance.asInstanceOf[AnyRef]),
                    methodName.toString.replaceFirst("g", "s"), Seq(t))
                  loop(newInstance, methodReturnType)
                //loop(mirror.reflect(methodReturnType).instance, methodReturnType)
              }
            } catch {
              case e: ClassNotFoundException => e.printStackTrace()
            }
          }
        }
      }
      topLevelObj
    }

    loop(topLevelObj, c.tpe)
  }

  private def generateLists[A](methodTypeArgs: List[Type], runtimeM: Mirror, obj: Any,
                               methodName: String, loop: (Any, Type) => A, isNested: Boolean): Unit = {

    val listTypeArg = methodTypeArgs.head
    val listTypeSymbolFullName = resolveTypeSymbolFullName(listTypeArg.typeSymbol.typeSignature)
    val listTypeParam = runtimeM.runtimeClass(listTypeArg.typeSymbol.asClass)

    if (isNested) {
      listTypeParam match {
        case ltp if ltp == classOf[Object] =>
        //Do nothing
        case ltp if ltp == classOf[String] =>
          obj.asInstanceOf[java.util.List[String]].addAll(strListGen.sample.get)
        case ltp if ltp == classOf[java.lang.Integer] =>
          obj.asInstanceOf[java.util.List[java.lang.Integer]].addAll(intListGen.sample.get)
        case ltp if ltp == classOf[java.lang.Double] =>
          obj.asInstanceOf[java.util.List[java.lang.Double]].addAll(doubleListGen.sample.get)
        case ltp if ltp == classOf[java.lang.Boolean] =>
          obj.asInstanceOf[java.util.List[java.lang.Boolean]].addAll(booleanListGen.sample.get)
        case ltp if ltp.isEnum =>
          val enumValues = getEnumValues(ltp.asInstanceOf[Class[Any]])
          obj.asInstanceOf[java.util.List[Any]].addAll(enumListGen(2, enumValues).sample.get)
        case ltp if ltp == classOf[java.util.List[_]] =>
          val numOfElemInOuterArr = 2
          val outerArr = new util.ArrayList[java.util.List[Any]]()

          val innerArrays = (1 to numOfElemInOuterArr).map(_ => new util.ArrayList[Any]())
          innerArrays.foreach(outerArr.add)

          obj.asInstanceOf[java.util.List[java.util.List[Any]]].addAll(outerArr)

          innerArrays.foreach(nestedArray => generateLists(listTypeArg.typeArgs, runtimeM, nestedArray, methodName, loop, isNested = true))
        case _ =>
          val numOfInstancesToGenerate = 2
          val instanceList = (1 to numOfInstancesToGenerate).map(_ => Class.forName(listTypeSymbolFullName).newInstance())

          obj.asInstanceOf[java.util.List[Any]].addAll(instanceList.asJava)

          instanceList.foreach(ins => loop(ins, listTypeArg))
      }
    } else {
      listTypeParam match {
        case ltp if ltp == classOf[Object] =>
        //Do nothing
        case ltp if ltp == classOf[String] =>
          invokeMethod(obj, Seq(strListGen.sample.get),
            methodName.replaceFirst("g", "s"), Seq(classOf[java.util.List[_]]))
        case ltp if ltp == classOf[java.lang.Integer] =>
          invokeMethod(obj, Seq(intListGen.sample.get),
            methodName.replaceFirst("g", "s"), Seq(classOf[java.util.List[_]]))
        case ltp if ltp == classOf[java.lang.Double] =>
          invokeMethod(obj, Seq(doubleListGen.sample.get),
            methodName.replaceFirst("g", "s"), Seq(classOf[java.util.List[_]]))
        case ltp if ltp == classOf[java.lang.Boolean] =>
          invokeMethod(obj, Seq(booleanListGen.sample.get),
            methodName.replaceFirst("g", "s"), Seq(classOf[java.util.List[_]]))
        case ltp if ltp.isEnum =>
          val enumValues = getEnumValues(ltp.asInstanceOf[Class[Any]])
          invokeMethod(obj, Seq(enumListGen(2, enumValues).sample.get),
            methodName.replaceFirst("g", "s"), Seq(classOf[java.util.List[_]]))
        case ltp if ltp == classOf[java.util.List[_]] =>
          val numOfElemInOuterArr = 2
          val outerArr = new util.ArrayList[java.util.List[Any]]()

          val innerArrays = (1 to numOfElemInOuterArr).map(_ => new util.ArrayList[Any]())
          innerArrays.foreach(outerArr.add)

          invokeMethod(obj, Seq(outerArr),
            methodName.replaceFirst("g", "s"), Seq(classOf[java.util.List[java.util.List[_]]]))
          innerArrays.foreach(nestedArray => generateLists(listTypeArg.typeArgs, runtimeM, nestedArray, methodName, loop, isNested = true))
        case _ =>
          val numOfInstancesToGenerate = 2
          val instanceList = (1 to numOfInstancesToGenerate).map(_ => Class.forName(listTypeSymbolFullName).newInstance())

          invokeMethod(obj, Seq(instanceList.asJava),
            methodName.replaceFirst("g", "s"), Seq(classOf[java.util.List[_]]))

          instanceList.foreach(ins => loop(ins, listTypeArg))
      }
    }
  }

  private def getEnumValues[E](enumClass: Class[E]): Array[E] = {
    val f = enumClass.getDeclaredField("$VALUES")
    f.setAccessible(true)
    val o = f.get(null)

    o.asInstanceOf[Array[E]]
  }

  private def resolveTypeSymbolFullName(methodReturnType: Type): String = {
    val typeS = methodReturnType.typeSymbol.asType
    val typeSFullName = typeS.fullName

    val typeSArr = typeSFullName.split("\\.").toList
    val isInnerEnum = typeSArr.lift(typeSArr.size - 2).exists(_.head.isUpper)

    if (typeS.isJavaEnum && isInnerEnum)
      typeS.fullName.patch(typeS.fullName.lastIndexOf('.'), "$", 1)
    else typeS.fullName
  }
}
