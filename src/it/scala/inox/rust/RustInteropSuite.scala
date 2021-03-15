/* Copyright 2009-2018 EPFL, Lausanne */

package inox
package rust

import org.scalatest._

import java.io.{FileInputStream, InputStream}

import scala.reflect.ClassTag

class RustInteropSuite extends FunSpec with ResourceUtils {
  import inox.trees._

  val ctx = TestContext.empty

  val files = resourceFiles("regression/rust", filter = _ endsWith ".inoxser", recursive = false)

  describe("Deserializing from rust") {
    for (file <- files) {
      val name = file.getName
      val fis = new FileInputStream(file)
      val serializer = utils.Serializer(inox.trees)
      import serializer._

      def test[T: ClassTag](expected: T)(implicit p: SerializableOrProcedure[T]): Unit = {
        val data = serializer.deserialize[T](fis)
        assert(data == expected)
      }

      it(s"deserializes $name") {
        name match {
          case "seq_of_ints.inoxser" =>
            test(Seq[Int](1, 2, 3))
          case "many_seqs.inoxser" =>
            test((Seq[Int](1, 2, 3), Seq[Boolean](true, false), Seq[String]("Hello", "world")))
          case "set_of_int_tuples.inoxser" =>
            test(Set[(Int, Int)]((1, 2), (1, 3), (2, 3), (-4, 5)))
          case "map_of_strings_and_ints.inoxser" =>
            test(Map[String, Int](("alpha", 123), ("bravo", 456), ("charlie", 789)))
          case "option_of_bigint.inoxser" =>
            test(Some(BigInt(123)))
        }
      }
    }
  }
}
