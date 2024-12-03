package io.kaizensolutions.tusk.data

import io.kaizensolutions.tusk.codec.*
import io.vertx.pgclient.data.Point as VertxPoint
import io.vertx.sqlclient.{Row as VertxRow, Tuple as VertxTuple}

final case class Point(x: Double, y: Double):
  private[tusk] def toVertx: VertxPoint =
    new io.vertx.pgclient.data.Point(x, y)

object Point:
  extension (point: VertxPoint)
    def toScala: Point =
      Point(point.x, point.y)

  given Encoder[Point] = new Encoder[Point]:
    override def encode(value: Point, into: VertxTuple): VertxTuple =
      into.addValue(value.toVertx)

  given Decoder[Point] = new Decoder[Point]:
    override def indexed(row: VertxRow, index: Int): Point =
      row.get(classOf[VertxPoint], index).toScala

    override def named(row: VertxRow, name: String): Point =
      row.get(classOf[VertxPoint], name).toScala
