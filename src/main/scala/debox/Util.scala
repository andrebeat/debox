package debox

import annotation.tailrec
import scala.math.{min, max}
import scala.{specialized => spec}

import language.experimental.macros

//import scala.reflect.{ClassTag, TypeTag}
import scala.reflect.ClassTag
import scala.reflect.macros.Context

object Util {
  def alloc[@spec A:ClassTag](src:Array[A], s1:Int, len:Int) = {
    val as = new Array[A](len)
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
  def arrayMacro[A:c.WeakTypeTag](c:Context)(as:c.Expr[A]*): c.Expr[Array[A]] = {
    import c.mirror._
    import c.universe._
    def const(x:Int) = Literal(Constant(x))

    val n = as.length
    val arr = newTermName("arr")

    val mod = Ident(staticModule("scala.reflect.ClassTag"))
    val att = implicitly[c.WeakTypeTag[A]]
    val ct = Apply(mod, List(c.reifyRuntimeClass(att.tpe)))

    val create = Apply(Select(ct, "newArray"), List(const(n)))
    val arrtpe = TypeTree(implicitly[c.WeakTypeTag[Array[A]]].tpe)
    val valdef = ValDef(Modifiers(), arr, arrtpe, create)

    val updates = (0 until n).map {
      i => Apply(Select(Ident(arr), "update"), List(const(i), as(i).tree))
    }

    val exprs = Seq(valdef) ++ updates ++ Seq(Ident(arr))
    val block = Block(exprs:_*)

    c.Expr[Array[A]](block)
  }

  // some very simple inlines to help with bit-twiddling
  @inline final def shift(i:Int) = (i & 15) << 1
  @inline final def shifted(bs: Array[Int], i:Int) = bs(i >> 4) >> shift(i)
  @inline final def ored(bs: Array[Int], i:Int, v:Int) = bs(i >> 4) |= v
  @inline final def anded(bs: Array[Int], i:Int, v:Int) = bs(i >> 4) &= v

  /**
   * Return the status of bucket 'i'.
   *
   * 3 means the bucket is defining a key/value
   * 2 means the bucket was previously used but is currently empty
   * 0 means the bucket is unused
   *
   * The distinction betwee 3 and 2 is important when keys have been deleted.
   * In these cases, a previous collision still needs to be maintained (for
   * look up), although for inserting new keys, the bucket can be used.
   */
  @inline final def status(bs: Array[Int], i:Int): Int = shifted(bs, i) & 3

  /**
   * Mark bucket 'i' as in-use (3).
   */
  @inline final def set(bs: Array[Int], i:Int):Unit = ored(bs, i, 3 << shift(i))

  /**
   * Unmark bucket 'i' (unset the 1 bit, so 3 => 2, 0 => 0).
   */
  @inline final def unset(bs: Array[Int], i:Int):Unit = anded(bs, i, ~(1 << shift(i)))

  def nextPowerOfTwo(n: Int): Int = {
    val x = java.lang.Integer.highestOneBit(n)
    if (x == n) n else x * 2
  }
}
