package io.kaizensolutions.tusk

import fs2.Chunk
import io.vertx.sqlclient.Tuple as VertxTuple
import io.kaizensolutions.tusk.interpolation.ValueInSql

/**
 * A wrapper around a chunk of `ValueInSql` that allows for easy conversion to a
 * `VertxTuple`.
 */
opaque type SqlValues = Chunk[ValueInSql]
object SqlValues:
  def apply(in: Chunk[ValueInSql]): SqlValues = in

  extension (value: SqlValues)
    inline def isEmpty: Boolean = value.isEmpty

    inline def toVertx: VertxTuple =
      var acc = VertxTuple.tuple()
      value.foreach: vsql =>
        val updated = vsql.encoder.encode(vsql.value, acc)
        acc = updated
      acc
