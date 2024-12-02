package io.kaizensolutions.tusk.interpolation

import io.kaizensolutions.tusk.codec.Encoder

trait ValueInSql:
  type Value
  val value: Value
  val encoder: Encoder[Value]

  override def toString: String = s"ValueInSql(value = $value, encoder = $encoder)"

object ValueInSql:
  type Out[A] = ValueInSql:
    type Value = A 

  def apply[A](value: A)(using encoder: Encoder[A]): ValueInSql.Out[A] = make(value, encoder)

  def make[A](v: A, e: Encoder[A]): ValueInSql.Out[A] = new ValueInSql:
    type Value = A
    val value = v
    val encoder = e

  given [A](using encoder: Encoder[A]): Conversion[A, ValueInSql] = (a:A) => 
    make(a, encoder)