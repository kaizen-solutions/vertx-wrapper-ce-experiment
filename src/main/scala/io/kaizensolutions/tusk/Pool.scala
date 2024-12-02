package io.kaizensolutions.tusk

import cats.effect.*
import cats.effect.Resource.ExitCase
import cats.effect.kernel.Resource.ExitCase.*
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import io.kaizensolutions.tusk.codec.*
import io.kaizensolutions.tusk.interpolation.SqlInterpolatedString
import io.vertx.pgclient.{PgBuilder, PgConnection}
import io.vertx.sqlclient.{ClientBuilder, Pool as VertxPool, Row, Tuple as VertxTuple}

import scala.jdk.CollectionConverters.*

final class Pool[F[_]](
  underlying: VertxPool
)(using A: Async[F]):
  def executeBatch[In](sqlString: String, values: Chunk[In])(using encoder: Encoder.Batch[In]): F[Chunk[Row]] =
    val batchValues =
      values.map(value => encoder.encode(value, VertxTuple.tuple())).toList.asJava

    fromVertx(underlying.preparedQuery(sqlString).executeBatch(batchValues))
      .map(Chunk.fromRowSet)

  def query(sql: String): F[Chunk[Row]] =
    fromVertx(underlying.query(sql).execute())
      .map(Chunk.fromRowSet)

  def query(query: SqlInterpolatedString): F[Chunk[Row]] =
    val (sqlString, values) = query.render
    fromVertx(underlying.preparedQuery(sqlString).execute(values.toVertx))
      .map(Chunk.fromRowSet)

  def queryStream(query: SqlInterpolatedString, fetchSize: Int = 512): Stream[F, Row] =
    val (sqlString, values) = query.render
    Stream
      .resource(advanced.connection)
      .flatMap: cxn =>
        val txn = Stream.bracketCase(cxn.transaction.beginOrReuse)((txn, exitCase) =>
          exitCase match
            case ExitCase.Succeeded                      => txn.commit
            case ExitCase.Errored(_) | ExitCase.Canceled => txn.rollback
        )
        val fetch = Stream.force(cxn.prepare(sqlString).map(_.stream(values, fetchSize)))

        txn >> fetch

  def transaction: Resource[F, Connection[F]] =
    advanced.connection
      .flatMap: lowCxn =>
        val begin = lowCxn.transaction.begin
        val finalize = (txn: Transaction, exitCase: ExitCase) =>
          exitCase match
            case ExitCase.Succeeded                      => txn.commit
            case ExitCase.Errored(_) | ExitCase.Canceled => txn.rollback

        Resource
          .makeCase(begin)(finalize)
          .as(Connection.from(lowCxn))

  object advanced:
    def connection: Resource[F, LowLevelConnection[F]] =
      val create: F[LowLevelConnection[F]] = fromVertx(underlying.getConnection()).map(LowLevelConnection.from)

      def teardown(cxn: LowLevelConnection[F], exit: ExitCase): F[Unit] = exit match
        case Succeeded  => cxn.close
        case Errored(e) => cxn.close
        case Canceled   => cancel(cxn).guarantee(cxn.close)

      Resource.makeCase(create)(teardown)

    // Postgres feature only
    private def cancel(cxn: LowLevelConnection[F]): F[Unit] =
      val postgresConnection = PgConnection.cast(cxn.escapeHatch)
      fromVertx(postgresConnection.cancelRequest()).void

object Pool:
  def make[F[_]](configure: ClientBuilder[VertxPool] => ClientBuilder[VertxPool])(using
    A: Async[F]
  ): Resource[F, Pool[F]] =
    val create                         = A.delay(configure(PgBuilder.pool()).build())
    val teardown: VertxPool => F[Unit] = (client: VertxPool) => fromVertx(client.close()).void
    Resource.make(create)(teardown).map(Pool(_))
