package io.kaizensolutions.tusk

import cats.effect.Async
import cats.syntax.all.*
import fs2.*
import io.kaizensolutions.tusk.*
import io.vertx.sqlclient.{Row, SqlConnection as VertxSqlConnection}

import scala.jdk.CollectionConverters.*

final class Connection[F[_]](cxn: LowLevelConnection[F])(using A: Async[F]):
  def query(sqlString: String): F[Chunk[Row]] = cxn.query(sqlString)

  def prepare(sqlString: String): F[PreparedStatement[F]] = cxn.prepare(sqlString)

  private[tusk] def escapeHatch: LowLevelConnection[F] = cxn

object Connection:
  def from[F[_]: Async](cxn: LowLevelConnection[F]): Connection[F] = Connection(cxn)

final class LowLevelConnection[F[_]](cxn: VertxSqlConnection)(using A: Async[F]):

  def query(sqlString: String): F[Chunk[Row]] =
    fromVertx(cxn.query(sqlString).execute())
      .map(rowSet => Chunk.iterator(rowSet.iterator().asScala))

  def prepare(sqlString: String): F[PreparedStatement[F]] =
    fromVertx:
      cxn.prepare(sqlString).map(PreparedStatement(_))

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
