package io.kaizensolutions.tusk

import cats.syntax.all.*
import cats.effect.Async
import io.kaizensolutions.tusk.*
import io.vertx.sqlclient.{SqlConnection as VertxSqlConnection, Row}
import scala.jdk.CollectionConverters.*
import fs2.*
import io.kaizensolutions.tusk.interpolation.SqlInterpolatedString

final class Connection[F[_]](cxn: LowLevelConnection[F])(using A: Async[F]):
  def query(sql: String): F[Chunk[Row]] = cxn.query(sql)

  private[tusk] def escapeHatch: LowLevelConnection[F] = cxn

object Connection:
  def from[F[_]: Async](cxn: LowLevelConnection[F]): Connection[F] = Connection(cxn)

final class LowLevelConnection[F[_]](cxn: VertxSqlConnection)(using A: Async[F]):
  def query(sql: String): F[Chunk[Row]] =
    fromVertx(cxn.query(sql).execute())
      .map(rowSet => Chunk.iterator(rowSet.iterator().asScala))

  def prepare(sql: String): F[PreparedStatement[F]] =
    fromVertx:
      cxn.prepare(sql).map(PreparedStatement(_))

  val close: F[Unit] = fromVertx(cxn.close()).void

  private[tusk] val escapeHatch: VertxSqlConnection = cxn

  object transaction:
    def begin: F[Transaction] = 
      fromVertx(cxn.begin()).map(Transaction.from(_))

    def beginOrReuse: F[Transaction] =
      val current = cxn.transaction()
      if current != null then A.pure(Transaction.from(current))
      else begin

object LowLevelConnection:
  def from[F[_]: Async](cxn: VertxSqlConnection): LowLevelConnection[F] =
    LowLevelConnection(cxn)
