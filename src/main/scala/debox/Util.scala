package debox

import annotation.tailrec
import scala.math.{min, max}
import scala.{specialized => spec}

import language.experimental.macros

import scala.reflect.{ClassTag, TypeTag}
import scala.reflect.macros.Context

object Util {
  def alloc[@spec A:ClassTag](src:Array[A], s1:Int, len:Int) = {
    val as = Array.ofDim[A](len)
    System.arraycopy(src, s1, as, 0, len)
    as
  }

  /**
   * Efficient alternative to Array.apply.
   *
   * "As seen on scala-internals!"
   */
  def array[A](as:A*) = macro arrayMacro[A]

  /**
   * Takes in something like:
   *   ArrayUtil.alloc[Int](11, 22, 33, 44)(ct)
   *
   * and builds a tree like:
   *   {
   *     val arr:Array[Int] = ct.newArray(4)
   *     arr.update(0, 11)
   *     arr.update(1, 22)
   *     arr.update(2, 33)
   *     arr.update(3, 44)
   *     arr
   *   }
   */
  def arrayMacro[A:c.AbsTypeTag](c:Context)(as:c.Expr[A]*): c.Expr[Array[A]] = {
    import c.mirror._
    import c.universe._
    def const(x:Int) = Literal(Constant(x))

    val n = as.length
    val arr = newTermName("arr")

    val mod = Ident(staticModule("scala.reflect.ClassTag"))
    val att = implicitly[c.AbsTypeTag[A]]
    val ct = Apply(mod, List(c.reifyRuntimeClass(att.tpe)))

    val create = Apply(Select(ct, "newArray"), List(const(n)))
    val arrtpe = TypeTree(implicitly[c.AbsTypeTag[Array[A]]].tpe)
    val valdef = ValDef(Modifiers(), arr, arrtpe, create)

    val updates = (0 until n).map {
      i => Apply(Select(Ident(arr), "update"), List(const(i), as(i).tree))
    }

    val exprs = Seq(valdef) ++ updates ++ Seq(Ident(arr))
    val block = Block(exprs:_*)

    c.Expr[Array[A]](block)
  }

}
