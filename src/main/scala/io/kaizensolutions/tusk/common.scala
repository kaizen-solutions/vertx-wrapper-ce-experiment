package io.kaizensolutions.tusk

import cats.syntax.functor.*
import cats.effect.*
import fs2.Chunk
import scala.jdk.CollectionConverters.*
import io.kaizensolutions.tusk.codec.Decoder
import io.kaizensolutions.tusk.interpolation.ValueInSql
import io.vertx.sqlclient.Tuple as VertxTuple

private[tusk] def fromVertx[F[_], A](thunk: => io.vertx.core.Future[A])(using A: Async[F]): F[A] =
  A.fromCompletionStage(A.delay(thunk.toCompletionStage()))

extension (c: Chunk[ValueInSql])
  def toVertx: VertxTuple =
    var acc = VertxTuple.tuple()
    c.foreach: vsql =>
      val updated = vsql.encoder.encode(vsql.value, acc)
      acc = updated
    acc

extension (c: Chunk.type)
  def fromRowSet(rowSet: io.vertx.sqlclient.RowSet[io.vertx.sqlclient.Row]): Chunk[io.vertx.sqlclient.Row] =
    Chunk.from(rowSet.asScala)

extension[F[_]] (rows: F[Chunk[io.vertx.sqlclient.Row]])
  def indexedDecode[A](using decoder: Decoder[A], S: Sync[F]): F[Chunk[A]] =
    rows.map(chunk => chunk.map(decoder.indexed(_, 0)))

  def labelledDecode[A](using decoder: Decoder[A], S: Sync[F]): F[Chunk[A]] =
    rows.map(chunk => chunk.map(decoder.named(_, "_unused")))
