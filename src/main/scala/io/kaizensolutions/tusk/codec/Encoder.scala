package io.kaizensolutions.tusk.codec

import fs2.Chunk
import io.circe.Json
import io.vertx.core.buffer.Buffer
import io.vertx.sqlclient.Tuple as VertxTuple
import shapeless3.deriving.K0

import java.time.*
import java.util.UUID
import scala.annotation.implicitNotFound
import io.vertx.core.json.{JsonArray as VertxJsonArray, JsonObject as VertxJsonObject}

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

  /**
   * Note: If you choose to encode Json.String, you need to escape all double
   * quotes and surround the string with quotes. This is due to the underlying
   * Vertx driver's limitation
   */
  given Encoder[Json] = (value, into) =>
    value.fold(
      jsonNull = into.addValue(null),
      jsonBoolean = into.addBoolean(_),
      jsonNumber = num => into.addDouble(num.toDouble),
      jsonString = into.addString(_),
      jsonArray = arr => into.addJsonArray(VertxJsonArray(value.noSpaces)),
      jsonObject = obj => into.addJsonObject(VertxJsonObject(value.noSpaces))
    )

  given Encoder[BigDecimal] = (value, into) => into.addBigDecimal(value.bigDecimal)

  // More here: https://vertx.io/docs/vertx-pg-client/java/#_postgresql_type_mapping

  /**
   * This is mostly a marker trait to help identify a case class encoder but it
   * does have a way to generate value placeholders if you need to insert or
   * update
   *   - Supports batched inserts/updates
   *   - Can also be used to generate the values placeholder for a query
   *   - You can also use this for normal inserts/updates as long as you ensure
   *     the case class fields are in the same order as the query
   */
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
