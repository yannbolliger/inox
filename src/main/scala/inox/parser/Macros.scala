package inox
package parser

import scala.reflect.macros.whitebox.Context
import scala.language.experimental.macros

abstract class Macros(final val c: Context) extends Parsers with IRs {
  import c.universe.{Type => _, Function => _, Expr => _, If => _,  _}

  protected val interpolator: c.Tree

  private val self = {
    val Select(self, _) = c.prefix.tree
    self
  }

  private val sc = StringContext({

    def getString(expr: c.Tree): String = expr match {
      case Literal(Constant(s : String)) => s
    }

    val ls = self match {
      case Block(ValDef(_, _, _, Apply(_, ls)) :: _, _) => {
        // TODO: Should we issue a warning ?
        // c.warning(c.enclosingPosition, "No implicit Symbols in scope. Using NoSymbols by default.")
        ls  // In case of default symbols.
      }
      case Apply(Apply(_, Apply(_, ls) :: _), _) => ls  // In case of implicit symbols.
      case _ => c.abort(c.enclosingPosition, "Unexpected macro use.")
    }

    ls.map(getString)
  }: _*)

  import Identifiers._
  import Bindings._
  import ADTs._
  import Functions._
  import Types._
  import Exprs._

  implicit lazy val bigIntLiftable: Liftable[BigInt] = Liftable[BigInt] {
    case n => q"_root_.scala.math.BigInt(${n.toString})"
  }

  implicit def hseqLiftable[A <: IR](implicit ev: Liftable[A]) = Liftable[HSeq[A]] {
    case HSeq(es) =>
      q"$interpolator.HSeq.fromSeq(_root_.scala.collection.immutable.Seq(..$es))"
  }

  implicit lazy val identifiersLiftable: Liftable[Identifier] = Liftable[Identifier] {
    case IdentifierName(name) =>
      q"$interpolator.Identifiers.IdentifierName($name)"
    case IdentifierHole(index) =>
      q"$interpolator.Identifiers.IdentifierHole($index)"
  }

  implicit lazy val bindingsLiftable: Liftable[Binding] = Liftable[Binding] {
    case InferredValDef(id) =>
      q"$interpolator.Bindings.InferredValDef($id)"
    case ExplicitValDef(id, tpe) =>
      q"$interpolator.Bindings.ExplicitValDef($id, $tpe)"
    case BindingHole(index) =>
      q"$interpolator.Bindings.BindingHole($index)"
  }

  implicit lazy val sortsLiftable: Liftable[Sort] = Liftable[Sort] {
    case Sort(id, tps, cs) =>
      q"$interpolator.ADTs.Sort($id, $tps, $cs)"
  }

  implicit lazy val constructorsLiftable: Liftable[Constructor] = Liftable[Constructor] {
    case Constructor(id, ps) =>
      q"$interpolator.ADTs.Constructor($id, $ps)"
  }

  implicit lazy val functionsLiftable: Liftable[Function] = Liftable[Function] {
    case Function(id, tps, ps, rt, b) =>
      q"$interpolator.Functions.Function($id, $tps, $ps, $rt, $b)"
  }

  implicit lazy val typesLiftable: Liftable[Type] = Liftable[Type] {
    case TypeHole(index) =>
      q"$interpolator.Types.TypeHole($index)"
    case Types.Primitive(prim) =>
      q"$interpolator.Types.Primitive($prim)"
    case Operation(op, elems) =>
      q"$interpolator.Types.Operation($op, $elems)"
    case FunctionType(froms, to) =>
      q"$interpolator.Types.FunctionType($froms, $to)"
    case TupleType(elems) =>
      q"$interpolator.Types.TupleType($elems)"
    case Types.Invocation(id, args) =>
      q"$interpolator.Types.Invocation($id, $args)"
    case Types.Variable(id) =>
      q"$interpolator.Types.Variable($id)"
    case RefinementType(b, p) =>
      q"$interpolator.Types.RefinementType($b, $p)"
    case PiType(bs, to) =>
      q"$interpolator.Types.PiType($bs, $to)"
    case SigmaType(bs, to) =>
      q"$interpolator.Types.SigmaType($bs, $to)"
  }

  implicit lazy val typePrimitivesLiftable: Liftable[Types.Primitives.Type] = Liftable[Types.Primitives.Type] {
    case Primitives.BVType(size) =>
      q"$interpolator.Types.Primitives.BVType($size)"
    case Primitives.IntegerType =>
      q"$interpolator.Types.Primitives.IntegerType"
    case Primitives.StringType =>
      q"$interpolator.Types.Primitives.StringType"
    case Primitives.CharType =>
      q"$interpolator.Types.Primitives.CharType"
    case Primitives.BooleanType =>
      q"$interpolator.Types.Primitives.BooleanType"
    case Primitives.UnitType =>
      q"$interpolator.Types.Primitives.UnitType"
    case Primitives.RealType =>
      q"$interpolator.Types.Primitives.RealType"
  }

  implicit lazy val typeOperatorsLiftable: Liftable[Types.Operators.Operator] = Liftable[Types.Operators.Operator] {
    case Operators.Set =>
      q"$interpolator.Types.Operators.Set"
    case Operators.Map =>
      q"$interpolator.Types.Operators.Map"
    case Operators.Bag =>
      q"$interpolator.Types.Operators.Bag"
  }

  implicit lazy val exprsLiftable: Liftable[Expr] = Liftable[Expr] {
    case ExprHole(index) =>
      q"$interpolator.Exprs.ExprHole($index)"
    case UnitLiteral() =>
      q"$interpolator.Exprs.UnitLiteral()"
    case BooleanLiteral(value) =>
      q"$interpolator.Exprs.BooleanLiteral($value)"
    case IntegerLiteral(value) =>
      q"$interpolator.Exprs.IntegerLiteral($value)"
    case FractionLiteral(num, denom) =>
      q"$interpolator.Exprs.FractionLiteral($num, denom)"
    case StringLiteral(value) =>
      q"$interpolator.Exprs.StringLiteral($value)"
    case CharLiteral(value) =>
      q"$interpolator.Exprs.CharLiteral($value)"
    case SetConstruction(t, es) =>
      q"$interpolator.Exprs.SetConstruction($t, $es)"
    case BagConstruction(t, ps) =>
      q"$interpolator.Exprs.BagConstruction($t, $ps)"
    case MapConstruction(ts, ps, d) =>
      q"$interpolator.Exprs.MapConstruction($ts, $ps, $d)"
    case Exprs.Variable(id) =>
      q"$interpolator.Exprs.Variable($id)"
    case UnaryOperation(op, expr) =>
      q"$interpolator.Exprs.UnaryOperation($op, $expr)"
    case BinaryOperation(op, lhs, rhs) =>
      q"$interpolator.Exprs.BinaryOperation($op, $lhs, $rhs)"
    case TernaryOperation(op, lhs, mid, rhs) =>
      q"$interpolator.Exprs.TernaryOperation($op, $lhs, $mid, $rhs)"
    case NaryOperation(op, args) =>
      q"$interpolator.Exprs.NaryOperation($op, $args)"
    case Exprs.Invocation(id, tps, args) =>
      q"$interpolator.Exprs.Invocation($id, $tps, $args)"
    case PrimitiveInvocation(id, tps, args) =>
      q"$interpolator.Exprs.PrimitiveInvocation($id, $tps, $args)"
    case Application(callee, args) =>
      q"$interpolator.Exprs.Application($callee, $args)"
    case Abstraction(q, bs, b) =>
      q"$interpolator.Exprs.Abstraction($q, $bs, $b)"
    case Let(b, v, e) =>
      q"$interpolator.Exprs.Let($b, $v, $e)"
    case If(c, t, e) =>
      q"$interpolator.Exprs.If($c, $t, $e)"
    case Selection(s, f) =>
      q"$interpolator.Exprs.Selection($s, $f)"
    case Tuple(es) =>
      q"$interpolator.Exprs.Tuple($es)"
    case TupleSelection(t, index) =>
      q"$interpolator.Exprs.TupleSelection($t, $index)"
    case TypeAnnotation(e, t) =>
      q"$interpolator.Exprs.TypeAnnotation($e, $t)"
    case Choose(b, p) =>
      q"$interpolator.Exprs.Choose($b, $p)"
    case Assume(p, b) =>
      q"$interpolator.Exprs.Assume($p, $b)"
    case IsConstructor(e, c) =>
      q"$interpolator.Exprs.IsConstructor($e, $c)"
    case Cast(m, e, t) =>
      q"$interpolator.Exprs.Cast($m, $e, $t)"
  }

  implicit lazy val exprPairsLiftable: Liftable[ExprPair] = Liftable[ExprPair] {
    case Pair(lhs, rhs) =>
      q"$interpolator.Exprs.Pair($lhs, $rhs)"
    case PairHole(index) =>
      q"$interpolator.Exprs.PairHole($index)"
  }

  implicit lazy val exprCastsLiftable: Liftable[Casts.Mode] = Liftable[Casts.Mode] {
    case Casts.Widen =>
      q"$interpolator.Exprs.Casts.Widen"
    case Casts.Narrow =>
      q"$interpolator.Exprs.Casts.Narrow"
  }

  implicit lazy val exprUnaryLiftable: Liftable[Unary.Operator] = Liftable[Unary.Operator] {
    case Unary.Minus =>
      q"$interpolator.Exprs.Unary.Minus"
    case Unary.Not =>
      q"$interpolator.Exprs.Unary.Not"
    case Unary.BVNot =>
      q"$interpolator.Exprs.Unary.BVNot"
    case Unary.StringLength =>
      q"$interpolator.Exprs.Unary.StringLength"
  }

  implicit lazy val exprBinaryLiftable: Liftable[Binary.Operator] = Liftable[Binary.Operator] {
    case Binary.Plus =>
      q"$interpolator.Exprs.Binary.Plus"
    case Binary.Minus =>
      q"$interpolator.Exprs.Binary.Minus"
    case Binary.Times =>
      q"$interpolator.Exprs.Binary.Times"
    case Binary.Division =>
      q"$interpolator.Exprs.Binary.Division"
    case Binary.Modulo =>
      q"$interpolator.Exprs.Binary.Modulo"
    case Binary.Remainder =>
      q"$interpolator.Exprs.Binary.Remainder"
    case Binary.Implies =>
      q"$interpolator.Exprs.Binary.Implies"
    case Binary.Equals =>
      q"$interpolator.Exprs.Binary.Equals"
    case Binary.LessEquals =>
      q"$interpolator.Exprs.Binary.LessEquals"
    case Binary.LessThan =>
      q"$interpolator.Exprs.Binary.LessThan"
    case Binary.GreaterEquals =>
      q"$interpolator.Exprs.Binary.GreaterEquals"
    case Binary.GreaterThan =>
      q"$interpolator.Exprs.Binary.GreaterThan"
    case Binary.BVAnd =>
      q"$interpolator.Exprs.Binary.BVAnd"
    case Binary.BVOr =>
      q"$interpolator.Exprs.Binary.BVOr"
    case Binary.BVXor =>
      q"$interpolator.Exprs.Binary.BVXor"
    case Binary.BVShiftLeft =>
      q"$interpolator.Exprs.Binary.BVShiftLeft"
    case Binary.BVAShiftRight =>
      q"$interpolator.Exprs.Binary.BVAShiftRight"
    case Binary.BVLShiftRight =>
      q"$interpolator.Exprs.Binary.BVLShiftRight"
    case Binary.StringConcat =>
      q"$interpolator.Exprs.Binary.StringConcat"
  }

  implicit lazy val exprTernaryLiftable: Liftable[Ternary.Operator] = Liftable[Ternary.Operator] {
    case Ternary.SubString =>
      q"$interpolator.Exprs.Ternary.SubString"
  }

  implicit lazy val exprNAryLiftable: Liftable[NAry.Operator] = Liftable[NAry.Operator] {
    case NAry.And =>
      q"$interpolator.Exprs.NAry.And"
    case NAry.Or =>
      q"$interpolator.Exprs.NAry.Or"
  }

  implicit lazy val exprPrimitivesLiftable: Liftable[Exprs.Primitive.Function] = Liftable[Exprs.Primitive.Function] {
    case Exprs.Primitive.SetAdd =>
      q"$interpolator.Exprs.Primitive.SetAdd"
    case Exprs.Primitive.ElementOfSet =>
      q"$interpolator.Exprs.Primitive.ElementOfSet"
    case Exprs.Primitive.SetIntersection =>
      q"$interpolator.Exprs.Primitive.SetIntersection"
    case Exprs.Primitive.SetUnion =>
      q"$interpolator.Exprs.Primitive.SetUnion"
    case Exprs.Primitive.SetDifference =>
      q"$interpolator.Exprs.Primitive.SetDifference"
    case Exprs.Primitive.Subset =>
      q"$interpolator.Exprs.Primitive.Subset"
    case Exprs.Primitive.BagAdd =>
      q"$interpolator.Exprs.Primitive.BagAdd"
    case Exprs.Primitive.MultiplicityInBag =>
      q"$interpolator.Exprs.Primitive.MultiplicityInBag"
    case Exprs.Primitive.BagIntersection =>
      q"$interpolator.Exprs.Primitive.BagIntersection"
    case Exprs.Primitive.BagUnion =>
      q"$interpolator.Exprs.Primitive.BagUnion"
    case Exprs.Primitive.BagDifference =>
      q"$interpolator.Exprs.Primitive.BagDifference"
    case Exprs.Primitive.MapApply =>
      q"$interpolator.Exprs.Primitive.MapApply"
    case Exprs.Primitive.MapUpdated =>
      q"$interpolator.Exprs.Primitive.MapUpdated"
  }

  implicit lazy val quantifiersLiftable: Liftable[Quantifier] = Liftable[Quantifier] {
    case Forall =>
      q"$interpolator.Exprs.Forall"
    case Lambda =>
      q"$interpolator.Exprs.Lambda"
  }
}