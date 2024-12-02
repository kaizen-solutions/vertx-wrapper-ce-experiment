package io.kaizensolutions.tusk.codec

import fs2.Chunk
import io.circe.{parser, Json}
import io.vertx.sqlclient.Row
import shapeless3.deriving.{K0, Labelling}

import java.time.*
import java.util.UUID
import scala.annotation.implicitNotFound

@implicitNotFound("No Decoder found for ${A}, please provide one")
trait Decoder[A]:
  self =>

  def indexed(row: Row, index: Int): A

  def named(row: Row, name: String): A

  def map[B](f: A => B): Decoder[B] =
    new Decoder[B]:
      def indexed(row: Row, index: Int): B = f(self.indexed(row, index))

      def named(row: Row, name: String): B = f(self.named(row, name))

object Decoder:
  given Decoder[Boolean] = new Decoder[Boolean]:
    def indexed(row: Row, index: Int): Boolean = row.getBoolean(index)

    def named(row: Row, name: String): Boolean = row.getBoolean(name)

  given Decoder[Short] = new Decoder[Short]:
    def indexed(row: Row, index: Int): Short = row.getShort(index)

    def named(row: Row, name: String): Short = row.getShort(name)

  given Decoder[Int] = new Decoder[Int]:
    def indexed(row: Row, index: Int): Int = row.getInteger(index)

    def named(row: Row, name: String): Int = row.getInteger(name)

  given Decoder[Long] = new Decoder[Long]:
    def indexed(row: Row, index: Int): Long = row.getLong(index)

    def named(row: Row, name: String): Long = row.getLong(name)

  given Decoder[Float] = new Decoder[Float]:
    def indexed(row: Row, index: Int): Float = row.getFloat(index)

    def named(row: Row, name: String): Float = row.getFloat(name)

  given Decoder[Double] = new Decoder[Double]:
    def indexed(row: Row, index: Int): Double = row.getDouble(index)

    def named(row: Row, name: String): Double = row.getDouble(name)

  given Decoder[String] = new Decoder[String]:
    def indexed(row: Row, index: Int): String = row.getString(index)

    def named(row: Row, name: String): String = row.getString(name)

  given Decoder[UUID] = new Decoder[UUID]:
    def indexed(row: Row, index: Int): UUID = row.getUUID(index)

    def named(row: Row, name: String): UUID = row.getUUID(name)

  given Decoder[LocalDate] = new Decoder[LocalDate]:
    def indexed(row: Row, index: Int): LocalDate = row.getLocalDate(index)

    def named(row: Row, name: String): LocalDate = row.getLocalDate(name)

  given Decoder[LocalTime] = new Decoder[LocalTime]:
    def indexed(row: Row, index: Int): LocalTime = row.getLocalTime(index)

    def named(row: Row, name: String): LocalTime = row.getLocalTime(name)

  given Decoder[OffsetTime] = new Decoder[OffsetTime]:
    def indexed(row: Row, index: Int): OffsetTime = row.getOffsetTime(index)

    def named(row: Row, name: String): OffsetTime = row.getOffsetTime(name)

  given Decoder[LocalDateTime] = new Decoder[LocalDateTime]:
    def indexed(row: Row, index: Int): LocalDateTime = row.getLocalDateTime(index)

    def named(row: Row, name: String): LocalDateTime = row.getLocalDateTime(name)

  given Decoder[OffsetDateTime] = new Decoder[OffsetDateTime]:
    def indexed(row: Row, index: Int): OffsetDateTime = row.getOffsetDateTime(index)

    def named(row: Row, name: String): OffsetDateTime = row.getOffsetDateTime(name)

  given Decoder[Chunk[Byte]] = new Decoder[Chunk[Byte]]:
    def indexed(row: Row, index: Int): Chunk[Byte] = Chunk.array(row.getBuffer(index).getBytes)

    def named(row: Row, name: String): Chunk[Byte] = Chunk.array(row.getBuffer(name).getBytes)

  given Decoder[Json] = new Decoder[Json]:
    def indexed(row: Row, index: Int): Json =
      parser.parse(row.getJson(index).toString()) match
        case Left(exception) => throw exception
        case Right(value)    => value

    def named(row: Row, name: String): Json =
      parser.parse(row.getJson(name).toString()) match
        case Left(exception) => throw exception
        case Right(value)    => value

  given decoderProduct[A](using deriver: K0.ProductInstances[Decoder, A], labelling: Labelling[A]): Decoder[A] =
    new Decoder[A]:
      def indexed(row: Row, ignoredIndex: Int): A =
        var currentIndex = 0
        deriver.construct:
          [piece] => { (pieceDecoder: Decoder[piece]) =>
            val piece = pieceDecoder.indexed(row, currentIndex)
            currentIndex += 1
            piece
          }

      def named(row: Row, ignoredName: String): A =
        if labelling.elemLabels.isEmpty then indexed(row, -1)
        else
          var currentIndex = 0
          deriver.construct:
            [piece] => { (pieceDecoder: Decoder[piece]) =>
              val piece = pieceDecoder.named(row, labelling.elemLabels(currentIndex))
              currentIndex += 1
              piece
            }

  // derivation limited to product types via ProductGeneric
  inline def derived[A](using deriver: K0.ProductGeneric[A]): Decoder[A] =
    decoderProduct[A]
