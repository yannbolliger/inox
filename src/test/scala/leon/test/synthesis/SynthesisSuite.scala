/* Copyright 2009-2014 EPFL, Lausanne */

package leon.test.synthesis
import leon.test._
import leon._
import leon.purescala.Definitions._
import leon.purescala.Trees._
import leon.purescala.TreeOps._
import leon.solvers.z3._
import leon.solvers.Solver
import leon.synthesis._
import leon.synthesis.graph._
import leon.synthesis.utils._
import leon.utils.PreprocessingPhase

import org.scalatest.matchers.ShouldMatchers._

import java.io.{BufferedWriter, FileWriter, File}

class SynthesisSuite extends LeonTestSuite {
  private var counter : Int = 0
  private def nextInt() : Int = {
    counter += 1
    counter
  }

  def forProgram(title: String, opts: SynthesisSettings = SynthesisSettings())(content: String)(block: (SearchContext, FunDef, Problem) => Unit) {

      test("Synthesizing %3d: [%s]".format(nextInt(), title)) {
        val ctx = testContext.copy(settings = Settings(
            synthesis = true,
            xlang     = false,
            verify    = false
          ))

        val pipeline = leon.utils.TemporaryInputPhase andThen leon.frontends.scalac.ExtractionPhase andThen PreprocessingPhase andThen SynthesisProblemExtractionPhase

        val (program, results) = pipeline.run(ctx)((content, Nil))

        for ((f,cis) <- results; ci <- cis) {
          info("%-20s".format(ci.fd.id.toString))

          val sctx = SynthesisContext(ctx,
                                      opts,
                                      ci.fd,
                                      program,
                                      ctx.reporter)

          val p      = ci.problem
          val search = new SimpleSearch(ctx, ci, p, opts.costModel, None)
          val hctx   = SearchContext(sctx, ci, search.g.root, search)

          block(hctx, f, p)
        }
      }
  }

  case class Apply(desc: String, andThen: List[Apply] = Nil)

  def synthesizeWith(hctx: SearchContext, p: Problem, ss: Apply): Solution = {
    val apps = hctx.sctx.rules flatMap { _.instantiateOn(hctx, p)}

    def matchingDesc(app: RuleInstantiation, ss: Apply): Boolean = {
      import java.util.regex.Pattern;
      val pattern = Pattern.quote(ss.desc).replace("*", "\\E.*\\Q")
      app.toString.matches(pattern)
    }

    apps.filter(matchingDesc(_, ss)) match {
      case app :: Nil =>
        app.apply(hctx) match {
          case RuleClosed(sols) =>
            assert(sols.nonEmpty)
            assert(ss.andThen.isEmpty)
            sols.head

          case RuleExpanded(sub) =>
            val subSols = (sub zip ss.andThen) map { case (p, ss) => synthesizeWith(hctx, p, ss) }
            app.onSuccess(subSols).get
        }

      case Nil =>
        throw new AssertionError("Failed to find "+ss.desc+". Available: "+apps.mkString(", "))

      case xs =>
        throw new AssertionError("Ambiguous "+ss.desc+". Found: "+xs.mkString(", "))
    }
  }

  def synthesize(title: String)(program: String)(strategies: PartialFunction[String, Apply]) {
    forProgram(title)(program) {
      case (hctx, fd, p) =>
        strategies.lift.apply(fd.id.toString) match {
          case Some(ss) =>
            synthesizeWith(hctx, p, ss)

          case None =>
            assert(false, "Function "+fd.id.toString+" not found")
        }
    }
  }


  def assertAllAlternativesSucceed(hctx: SearchContext, rr: Traversable[RuleInstantiation]) {
    assert(!rr.isEmpty)

    for (r <- rr) {
      assertRuleSuccess(hctx, r)
    }
  }

  def assertRuleSuccess(hctx: SearchContext, rr: RuleInstantiation): Option[Solution] = {
    rr.apply(hctx) match {
      case RuleClosed(sols) if sols.nonEmpty =>
        sols.headOption
      case _ =>
        assert(false, "Rule did not succeed")
        None
    }
  }

  forProgram("Ground Enum", SynthesisSettings(selectedSolvers = Set("enum")))(
    """
import leon.annotation._
import leon.lang._
import leon.lang.synthesis._

object Injection {
  sealed abstract class List
  case class Cons(tail: List) extends List
  case class Nil() extends List

  // proved with unrolling=0
  def size(l: List) : Int = (l match {
      case Nil() => 0
      case Cons(t) => 1 + size(t)
  }) ensuring(res => res >= 0)

  def simple() = choose{out: List => size(out) == 2 }
}
    """
  ) {
    case (hctx, fd, p) =>
      assertAllAlternativesSucceed(hctx, rules.Ground.instantiateOn(hctx, p))
  }

  forProgram("Cegis 1")(
    """
import leon.annotation._
import leon.lang._
import leon.lang.synthesis._

object Injection {
  sealed abstract class List
  case class Cons(tail: List) extends List
  case class Nil() extends List

  // proved with unrolling=0
  def size(l: List) : Int = (l match {
      case Nil() => 0
      case Cons(t) => 1 + size(t)
  }) ensuring(res => res >= 0)

  def simple(in: List) = choose{out: List => size(out) == size(in) }
}
    """
  ) {
    case (hctx, fd, p) =>
      assertAllAlternativesSucceed(hctx, rules.CEGIS.instantiateOn(hctx, p))
  }

  forProgram("Cegis 2")(
    """
import leon.annotation._
import leon.lang._
import leon.lang.synthesis._

object Injection {
  sealed abstract class List
  case class Cons(head: Int, tail: List) extends List
  case class Nil() extends List

  // simulates input/output spec.
  def tail(in: List) = choose {
    (out: List) =>
      (in != Cons(0, Nil())                   || out == Nil()) &&
      (in != Cons(0, Cons(1, Nil()))          || out == Cons(1, Nil())) &&
      (in != Cons(0, Cons(1, Cons(2, Nil()))) || out == Cons(1, Cons(2, Nil())))
  }
}
    """
  ) {
    case (hctx, fd, p) =>
      rules.CEGIS.instantiateOn(hctx, p).head.apply(hctx) match {
        case RuleClosed(sols) if sols.nonEmpty =>
          assert(false, "CEGIS should have failed, but found : %s".format(sols.head))
        case _ =>
      }

      rules.ADTSplit.instantiateOn(hctx, p).head.apply(hctx) match {
        case RuleExpanded(subs) =>
          for (sub <- subs; alt <- rules.CEGIS.instantiateOn(hctx, sub)) {
            assertRuleSuccess(hctx, alt)
          }
        case _ =>
          assert(false, "Woot?")
      }
  }


  synthesize("Lists")("""
import leon.lang._
import leon.lang.synthesis._

object SortedList {
  sealed abstract class List
  case class Cons(head: Int, tail: List) extends List
  case class Nil() extends List

  def size(l: List) : Int = (l match {
      case Nil() => 0
      case Cons(_, t) => 1 + size(t)
  }) ensuring(res => res >= 0)

  def content(l: List): Set[Int] = l match {
    case Nil() => Set.empty[Int]
    case Cons(i, t) => Set(i) ++ content(t)
  }

  def isSorted(l: List): Boolean = l match {
    case Nil() => true
    case Cons(x, Nil()) => true
    case Cons(x, Cons(y, ys)) => x <= y && isSorted(Cons(y, ys))
  }

  def concat(in1: List, in2: List) = choose {
    (out : List) =>
      content(out) == content(in1) ++ content(in2)
  }

  def insert(in1: List, v: Int) = choose {
    (out : List) =>
      content(out) == content(in1) ++ Set(v)
  }

  def insertSorted(in1: List, v: Int) = choose {
    (out : List) =>
      isSorted(in1) && content(out) == content(in1) ++ Set(v) && isSorted(out)
  }
}
    """) {
    case "concat" =>
      Apply("ADT Induction on 'in1'", List(
        Apply("CEGIS"),
        Apply("CEGIS")
      ))

    case "insert" =>
      Apply("ADT Induction on 'in1'", List(
        Apply("CEGIS"),
        Apply("CEGIS")
      ))

    case "insertSorted" =>
      Apply("Assert isSorted(in1)", List(
        Apply("ADT Induction on 'in1'", List(
          Apply("Ineq. Split on 'head*' and 'v*'", List(
            Apply("CEGIS"),
            Apply("CEGIS"),
            Apply("CEGIS")
          )),
          Apply("CEGIS")
        ))
      ))
  }

  synthesize("Church")("""
import leon.lang._
import leon.lang.synthesis._

object ChurchNumerals {
  sealed abstract class Num
  case object Z extends Num
  case class  S(pred: Num) extends Num

  def value(n:Num) : Int = {
    n match {
      case Z => 0
      case S(p) => 1 + value(p)
    }
  } ensuring (_ >= 0)

  // def add(x : Num, y : Num) : Num = (x match {
  //   case Z => y
  //   case S(p) => add(p, S(y))
  // }) ensuring (value(_) == value(x) + value(y))

  def add(x: Num, y: Num): Num = {
    choose { (r : Num) =>
      value(r) == value(x) + value(y)
    }
  }
}
    """) {
    case "add" =>
      Apply("ADT Induction on 'y'", List(
        Apply("CEGIS"),
        Apply("CEGIS")
      ))
  }

  synthesize("Church")("""
import leon.lang._
import leon.lang.synthesis._

object ChurchNumerals {
  sealed abstract class Num
  case object Z extends Num
  case class  S(pred: Num) extends Num

  def value(n:Num) : Int = {
    n match {
      case Z => 0
      case S(p) => 1 + value(p)
    }
  } ensuring (_ >= 0)

  def add(x : Num, y : Num) : Num = (x match {
    case Z => y
    case S(p) => add(p, S(y))
  }) ensuring (value(_) == value(x) + value(y))

  //def distinct(x : Num, y : Num) : Num = (x match {
  //  case Z =>
  //    S(y)

  //  case S(p) =>
  //    y match {
  //      case Z =>
  //        S(x)
  //      case S(p) =>
  //        Z
  //    }
  //}) ensuring (res => res != x  && res != y)

  def distinct(x: Num, y: Num): Num = {
    choose { (r : Num) =>
      r != x && r != y
    }
  }
}
    """) {
    case "distinct" =>
      Apply("CEGIS")
  }
}
