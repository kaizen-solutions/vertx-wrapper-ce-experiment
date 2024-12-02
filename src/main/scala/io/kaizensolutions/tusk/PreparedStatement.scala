package io.kaizensolutions.tusk

import io.vertx.sqlclient.PreparedStatement as VertxPreparedStatement
import fs2.*
import io.kaizensolutions.tusk.interpolation.ValueInSql
import cats.effect.Async
import cats.syntax.all.*
import io.vertx.sqlclient.*
import cats.effect.Resource.ExitCase

final class PreparedStatement[F[_]](ps: VertxPreparedStatement)(using A: Async[F]):
  println(s"PS HASHCODE ${ps.hashCode()}")
  def close: F[Unit] = fromVertx(ps.close()).void

  def query(values: Chunk[ValueInSql]): F[Chunk[Row]] =
    val fetchRowSet =
      fromVertx:
        if values.isEmpty then ps.query().execute()
        else ps.query().execute(values.toVertx)

    fetchRowSet.map(Chunk.fromRowSet)

  def stream(values: Chunk[ValueInSql], fetchSize: Int): Stream[F, Row] =
    val open: Pull[F, Row, Cursor] =
      Pull.eval:
        A.delay:
          if values.isEmpty then ps.cursor()
          else ps.cursor(values.toVertx)

    def tearDown(c: Cursor, exitCase: ExitCase): Pull[F, Row, Unit] = Pull.eval(fromVertx(c.close()).void)

    def use(cursor: Cursor): Pull[F, Row, Unit] =
      val emit =
        val fetchRows = fromVertx(cursor.read(fetchSize)).map(Chunk.fromRowSet)
        Pull.eval(fetchRows).flatMap(Pull.output)

      val cont: Pull[F, Row, Unit] =
        Pull.suspend:
          if cursor.hasMore() then use(cursor)
          else Pull.done

      emit >> cont

    Pull.bracketCase[F, Row, Cursor, Unit](open, use, tearDown).stream
