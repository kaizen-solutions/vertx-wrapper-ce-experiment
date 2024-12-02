package io.kaizensolutions.tusk

import cats.effect.*
import cats.effect.Resource.ExitCase
import cats.syntax.all.*
import io.vertx.sqlclient.{ClientBuilder, Pool as VertxPool, Row, Tuple as VertxTuple}
import io.vertx.pgclient.PgBuilder
import scala.jdk.CollectionConverters.*
import fs2.*
import scala.annotation.targetName
import io.kaizensolutions.tusk.codec.*
import io.kaizensolutions.tusk.interpolation.SqlInterpolatedString
import io.vertx.sqlclient.impl.cache.LruCache

final class Pool[F[_]](
  underlying: VertxPool
)(using A: Async[F]):
  def query(sql: String): F[Chunk[Row]] =
    fromVertx(underlying.query(sql).execute())
      .map(rowSet => Chunk.iterator(rowSet.iterator().asScala))

  def updateMany[In](sql: String, values: Chunk[In])(using encoder: Encoder.Batch[In]): F[Chunk[Row]] =
    val batchValues =
      values.map(value => encoder.encode(value, VertxTuple.tuple())).toList.asJava

    fromVertx(underlying.preparedQuery(sql).executeBatch(batchValues))
      .map: rowSet =>
        Chunk.iterator(rowSet.iterator().asScala)

  def queryStream(query: SqlInterpolatedString, fetchSize: Int = 512): Stream[F, Row] =
    val (sqlString, values) = query.render
    Stream
      .resource(advanced.connection)
      .flatMap: cxn =>
        val fetch = Stream.force(cxn.prepare(sqlString).map(_.stream(values, fetchSize)))
        val txn = Stream.bracketCase(cxn.transaction.beginOrReuse)((txn, exitCase) =>
          exitCase match
            case ExitCase.Succeeded => txn.commit
            case ExitCase.Errored(_) | ExitCase.Canceled => txn.rollback
        )
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
      val create: F[LowLevelConnection[F]]           = fromVertx(underlying.getConnection()).map(LowLevelConnection.from)
      val teardown: LowLevelConnection[F] => F[Unit] = _.close
      Resource.make(create)(teardown)

object Pool:
  def make[F[_]](configure: ClientBuilder[VertxPool] => ClientBuilder[VertxPool])(using
    A: Async[F]
  ): Resource[F, Pool[F]] =
    val create                         = A.delay(configure(PgBuilder.pool()).build())
    val teardown: VertxPool => F[Unit] = (client: VertxPool) => fromVertx(client.close()).void
    Resource.make(create)(teardown).map(Pool(_))
