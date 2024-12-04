package io.kaizensolutions.tusk

import cats.effect.*
import cats.syntax.functor.*
import fs2.*
import io.kaizensolutions.tusk.codec.Decoder
import io.vertx.sqlclient.{Row as VertxRow, RowSet as VertxRowSet}
import io.vertx.core.Future as VertxFuture

import scala.jdk.CollectionConverters.*

private[tusk] def fromVertx[F[_], A](thunk: => VertxFuture[A])(using A: Async[F]): F[A] =
  A.fromCompletionStage(A.delay(thunk.toCompletionStage()))

extension (c: Chunk.type)
  inline def fromRowSet(rowSet: VertxRowSet[VertxRow]): Chunk[io.vertx.sqlclient.Row] =
    Chunk.from(rowSet.asScala)

extension [F[_]](stream: Stream[F, VertxRow])
  def indexedDecode[A](using decoder: Decoder[A]): Stream[F, A] =
    stream.mapChunks(_.indexedDecode[A])

  def labelledDecode[A](using decoder: Decoder[A]): Stream[F, A] =
    stream.mapChunks(_.labelledDecode[A])

extension [F[_]](rows: F[Chunk[VertxRow]])
  def indexedDecode[A](using decoder: Decoder[A], S: Sync[F]): F[Chunk[A]] =
    rows.map(_.indexedDecode[A])

  def labelledDecode[A](using decoder: Decoder[A], S: Sync[F]): F[Chunk[A]] =
    rows.map(_.labelledDecode[A])

extension (rows: Chunk[VertxRow])
  def indexedDecode[A](using decoder: Decoder[A]): Chunk[A] =
    rows.map(decoder.indexed(_, 0))

  def labelledDecode[A](using decoder: Decoder[A]): Chunk[A] =
    rows.map(decoder.named(_, "_unused"))
