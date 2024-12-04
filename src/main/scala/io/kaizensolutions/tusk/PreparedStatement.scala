package io.kaizensolutions.tusk

import cats.effect.Async
import cats.effect.Resource.ExitCase
import cats.syntax.all.*
import fs2.*
import io.kaizensolutions.tusk.interpolation.ValueInSql
import io.vertx.sqlclient.{
  Cursor as VertxCursor,
  PreparedStatement as VertxPreparedStatement,
  Row as VertxRow,
  Tuple as VertxTuple
}
import io.kaizensolutions.tusk.codec.Encoder
import scala.jdk.CollectionConverters.*

final class PreparedStatement[F[_]](ps: VertxPreparedStatement)(using A: Async[F]):
  val close: F[Unit] = fromVertx(ps.close()).void

  def executeBatch[In](values: Chunk[In])(using encoder: Encoder.Batch[In]): F[Chunk[VertxRow]] =
    val batchValues =
      values.map(value => encoder.encode(value, VertxTuple.tuple())).toList.asJava

    executeBatch(batchValues)

  def executeBatch(values: Chunk[SqlValues]): F[Chunk[VertxRow]] =
    val batchValues =
      values.map(_.toVertx).toList.asJava

    executeBatch(batchValues)

  private inline def executeBatch(input: java.util.List[VertxTuple]): F[Chunk[VertxRow]] =
    fromVertx(ps.query().executeBatch(input))
      .map(Chunk.fromRowSet)

  def query[A](value: A)(using encoder: Encoder[A]): F[Chunk[VertxRow]] =
    val tuple = encoder.encode(value, VertxTuple.tuple())
    fromVertx(ps.query().execute(tuple))
      .map(Chunk.fromRowSet)

  def query(values: SqlValues): F[Chunk[VertxRow]] =
    val fetchRowSet =
      fromVertx:
        if values.isEmpty then ps.query().execute()
        else ps.query().execute(values.toVertx)

    fetchRowSet.map(Chunk.fromRowSet)

  def stream[A](value: A)(using encoder: Encoder[A], fetchSize: Int): Stream[F, VertxRow] =
    val tuple = encoder.encode(value, VertxTuple.tuple())
    stream(tuple, fetchSize)

  def stream(values: SqlValues, fetchSize: Int): Stream[F, VertxRow] =
    val tuple = values.toVertx
    stream(tuple, fetchSize)

  private inline def stream(tuple: VertxTuple, fetchSize: Int): Stream[F, VertxRow] =
    val open: Pull[F, VertxRow, VertxCursor] =
      Pull.eval:
        A.delay:
          ps.cursor(tuple)

    def tearDown(c: VertxCursor, exitCase: ExitCase): Pull[F, VertxRow, Unit] = Pull.eval(fromVertx(c.close()).void)

    def use(cursor: VertxCursor): Pull[F, VertxRow, Unit] =
      val emit =
        val fetchRows = fromVertx(cursor.read(fetchSize)).map(Chunk.fromRowSet)
        Pull.eval(fetchRows).flatMap(Pull.output)

      val cont: Pull[F, VertxRow, Unit] =
        Pull.suspend:
          if cursor.hasMore() then use(cursor)
          else Pull.done

      emit >> cont

    Pull.bracketCase[F, VertxRow, VertxCursor, Unit](open, use, tearDown).stream
