package io.kaizensolutions.tusk.codec

import fs2.Chunk
import io.circe.Json
import io.vertx.core.buffer.Buffer
import io.vertx.sqlclient.Tuple as VertxTuple
import shapeless3.deriving.K0

import java.time.*
import java.util.UUID
import scala.annotation.implicitNotFound

@FunctionalInterface
@implicitNotFound("No Encoder found for ${A}, please provide one")
trait Encoder[A]:
  def encode(value: A, into: VertxTuple): VertxTuple

  def contramap[B](f: B => A): Encoder[B] = (b, into) => encode(f(b), into)

object Encoder:
  given Encoder[Boolean] = (value, into) => into.addBoolean(value)

  given Encoder[Short] = (value, into) => into.addShort(value)

  given Encoder[Int] = (value, into) => into.addInteger(value)

  given Encoder[Long] = (value, into) => into.addLong(value)

  given Encoder[Float] = (value, into) => into.addFloat(value)

  given Encoder[Double] = (value, into) => into.addDouble(value)

  given Encoder[String] = (value, into) => into.addString(value)

  given Encoder[UUID] = (value, into) => into.addUUID(value)

  given Encoder[LocalDate] = (value, into) => into.addLocalDate(value)

  given Encoder[LocalTime] = (value, into) => into.addLocalTime(value)

  given Encoder[OffsetTime] = (value, into) => into.addOffsetTime(value)

  given Encoder[LocalDateTime] = (value, into) => into.addLocalDateTime(value)

  given Encoder[OffsetDateTime] = (value, into) => into.addOffsetDateTime(value)

  given Encoder[Chunk[Byte]] = (value, into) => into.addBuffer(Buffer.buffer(value.toArray))

  given Encoder[Json] = (value, into) => into.addString(value.noSpaces)

  // More here: https://vertx.io/docs/vertx-pg-client/java/#_postgresql_type_mapping

  // Supports batched inserts and updates
  trait Batch[A] extends Encoder[A]:
    // For example: case class ExampleRow(a: Int, b: String)
    // The valuesPlaceholder should return: ($1, $2)
    def valuesPlaceholder: String

  object Batch:
    def valuesPlaceholder[A](using batch: Encoder.Batch[A]): String = batch.valuesPlaceholder

    given productEncoder[A](using deriver: K0.ProductInstances[Encoder, A]): Encoder.Batch[A] =
      new Batch[A]:
        import scala.collection.mutable as mutable
        def valuesPlaceholder: String =
          var count = 1
          val (out, _) =
            deriver.unfold(mutable.StringBuilder().append("(")):
              [piece] =>
                (sb: mutable.StringBuilder, _: Encoder[piece]) =>
                  val acc = sb.append("$").append(count)
                  count += 1
                  (acc, Option.empty[piece])
          out.append(")").toString()

        def encode(value: A, acc: VertxTuple): VertxTuple =
          deriver.foldLeft[VertxTuple](value)(acc):
            [piece] => (acc: VertxTuple, pieceEncoder: Encoder[piece], p: piece) => pieceEncoder.encode(p, acc)

    inline def derived[A](using deriver: K0.ProductGeneric[A]): Encoder.Batch[A] =
      productEncoder[A]
