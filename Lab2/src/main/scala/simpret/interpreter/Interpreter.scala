package simpret.interpreter

import simpret.parser._
import simpret.errors._


abstract class EvaluationException(val message: String, val x: AST,
                                   private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

final class VariableCapturedEvaluationException(val var_id: String, val subst_s: AST,
                                                private val cause: Throwable = None.orNull)
  extends EvaluationException("variable (" + var_id + ") has been captured during substitution", subst_s, cause)

object Interpreter {
  def errVarCap(var_id: String, x: AST) = throw new VariableCapturedEvaluationException(var_id, x)

  /* function for defining which AST elements are values */
  def isvalue(x: AST) = x match {
    case BoolLit(_) => true
    case IntLit(_) => true
    case LamExp(_,_) => true
    case _ => false
  }

  /* function for determining the free variables of an expression */
  def freevars (x: AST) : List[String] = 
    x match {
      case Variable(id) => 
        List(id)
      case BoolLit(b) => 
        List()
      case IntLit(i) => 
        List()
      case CondExp(c, e1, e2) =>
        (freevars(c) ++ freevars(e1) ++ freevars(e2)).distinct
      case IsZeroExp(e) =>
        freevars(e)
      case PlusExp(e1, e2) =>
        (freevars(e1) ++ freevars(e2)).distinct
      case AssignExp(id, e) =>
        (List(id) ++ freevars(e)).distinct
      case SeqExp(e1, e2) =>
        (freevars(e1) ++ freevars(e2)).distinct
      case LamExp(id, e) =>
        freevars(e).filter(_ > id)
      case AppExp(e1, e2) =>
        freevars(e1) ++ freevars(e2)
    }

  /* function for carrying out a substitution */
  def subst (x: String, s: AST, t: AST) : AST = 
    if(freevars(s).contains(x)) {
      s match {
        case Variable(id) => 
          if(id == x) {
              t
          } else {
            s
          }
        case BoolLit(b) => 
          s
        case IntLit(i) => 
          s
        case CondExp(c, e1, e2) =>
          CondExp(subst(x, c, t), subst(x, e1, t), subst(x, e2, t))
        case IsZeroExp(e) =>
          IsZeroExp(subst(x, e, t))
        case PlusExp(e1, e2) =>
          PlusExp(subst(x, e1, t), subst(x, e2, t))
        case AssignExp(id, e) =>
          AssignExp(id, subst(x, e, t))
        case SeqExp(e1, e2) =>
          SeqExp(subst(x, e1, t), subst(x, e2, t))
        case LamExp(id, e) =>
          s
        case AppExp(e1, e2) =>
          AppExp(subst(x, e1, t), subst(x, e2, t))
      }
    } else {
      s
    }

  /* evaluation function for taking one step at a time */
  def step(x: AST, store: Map[String, AST]): Option[(AST, Map[String, AST])] = {
    x match {
      case Variable(id) =>
        store.get(id) match {
          case None => 
            None
          case Some(y) => 
            Some(y, store)
        }
      case BoolLit(b) =>
        None
      case IntLit(i) =>
        None
      case CondExp(c, e1, e2) =>
        c match {
          case BoolLit(true) =>
            Some(e1, store)
          case BoolLit(false) =>
            Some(e2, store)
          case _ =>
            val next = step(c, store)
            next match {
              case None =>
                None
              case Some((a: AST, s: Map[String, AST])) =>
                Some(CondExp(a, e1, e2), s)
            }
        }
      case IsZeroExp(e) =>
        e match {
          case IntLit(0) => 
            Some(BoolLit(true), store)
          case IntLit(i) =>
            Some(BoolLit(false), store)
          case _ =>
            val next = step(e, store)
            next match {
              case None =>
                None
              case Some((a: AST, s: Map[String, AST])) =>
                Some(IsZeroExp(a), s)
            }
        }
      case PlusExp(e1, e2) =>
        (e1, e2) match {
          case (IntLit(i1), IntLit(i2)) =>
            Some(IntLit(i1 + i2), store)
          case (IntLit(i1), e2) =>
            val next = step(e2, store)
            next match {
              case None =>
                None
              case Some((a: AST, s: Map[String, AST])) =>
                Some(PlusExp(IntLit(i1), a), s)
            }
          case (e1, e2) =>
            val next = step(e1, store)
            next match {
              case None =>
                None
              case Some((a: AST, s: Map[String, AST])) =>
                Some(PlusExp(a, e2), s)
            }
        }
      case AssignExp(id, e) =>
        isvalue(e) match {
          case true =>
            Some(e, store + (id -> e))
          case _ =>
            val next = step(e, store)
            next match {
              case None =>
                None
              case Some((a: AST, s: Map[String, AST])) =>
                Some(AssignExp(id, a), s)
            }
        }
      case SeqExp(e1, e2) =>
        val next = step(e1, store)
        next match {
          case None =>
            Some(e2, store)
          case Some((a: AST, s: Map[String, AST])) =>
            Some(SeqExp(a, e2), s)
        }
      case LamExp(id, e) =>
        None //Some(LamExp(id, e), store)
      case AppExp(e1, e2) =>
        e1 match {
          case LamExp(id, e) =>
            val fv = freevars(e1)
            val r = subst(id, e, e2)
            if(fv != freevars(r)) {
              errVarCap(id, r)
            }
            Some(r, store)
          case e1 =>
            val n1 = step(e1, store)
            n1 match {
              case None =>
                val n2 = step(e2, store)
                n2 match {
                  case None =>
                    None
                  case Some((a: AST, s: Map[String, AST])) =>
                    Some(AppExp(e1, a), s)
                }
              case Some((a: AST, s: Map[String, AST])) =>
                Some(AppExp(a, e2), s)
            }
        }
    }
  }

  /* evaluation function to iterate the steps of evaluation */
  val printSteps = false
  def eval(x: AST, store: Map[String, AST] = Map.empty): AST = {
    step(x, store) match {
      case None => {
        {
          // the rules couldn't be applied to reduce the expression further, now it's in normal form
          if (printSteps)
            print("\r\n========== done ==========\r\n")
          else
            ()
        }
        x
      }
      case Some ((x1, store1)) => {
        {
          // we could reduce the expression by applying the rules
          if (printSteps)
            print("\r\n========== step ==========\r\n" + ASTPrinter.convToStr(x1))
          else
            ()
        }
        eval(x1, store1)
      }
    }
  }

  /* function to apply the interpreter */
  def apply(x: AST): Either[EvaluationError, AST] = {
    try {
      Right(eval(x))
    } catch {
      case ex : EvaluationException =>
        val msg = ex.message
        val xe = ex.x
        val msg2 = msg + " -> \r\n" + ASTPrinter.convToStr(xe)
        Left(EvaluationError(Location.fromPos(xe.pos), msg2))
    }
  }
}
