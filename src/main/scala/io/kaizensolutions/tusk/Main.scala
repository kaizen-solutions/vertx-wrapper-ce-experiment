package com.user.land

import cats.syntax.all.*
import cats.effect.*
import fs2.*
import io.vertx.pgclient.*
import io.vertx.sqlclient.{Pool as VertxPool, *}
import cats.effect.Resource.ExitCase
import io.kaizensolutions.tusk.*
import io.kaizensolutions.tusk.codec.Decoder
import io.kaizensolutions.tusk.interpolation.ValueInSql
import io.kaizensolutions.tusk.codec.Encoder
import io.circe.Json
import io.circe.syntax.*
import scala.concurrent.duration.*

object Main extends IOApp.Simple:
  val run =
    val connectOptions =
      PgConnectOptions()
        .setPort(5432)
        .setHost("localhost")
        .setDatabase("postgres")
        .setUser("tusk")
        .setPassword("tusk")
        .setCachePreparedStatements(true)

    val poolOptions =
      PoolOptions().setMaxSize(5)

    def batchExample(pool: Pool[IO]): IO[Unit] =
      pool
        .updateMany(
          s"""INSERT INTO public.example(val_json) 
             VALUES ${summon[Encoder.Batch[ExampleRow]].valuesPlaceholder}
          """,
          Chunk(
            ExampleRow(Json.obj("foo" := "bar")),
            ExampleRow(Json.obj("foo" := "baz")),
            ExampleRow(Json.obj("foo" := "qux"))
          )
        )
        .map(_.map(_.deepToString()))
        .debug() *>
        pool
          .query("SELECT val_json from public.example")
          .indexedDecode[Json]
          .map(_.map(_.noSpaces))
          .debug()
          .void

    def cancelExample(pool: Pool[IO]): IO[Unit] =
      val dbSleep = pool.advanced.connection.use(_.query("select pg_sleep(10)").void >> IO.println("Sleep for 10s finished"))
      val runtimeSleep = IO.sleep(2.seconds) >> IO.println("Sleep for 2s finished")
      dbSleep.race(runtimeSleep).void

    def preparedStatementBadUsageExample(pool: Pool[IO]): IO[Unit] =
      pool.advanced.connection.use: cxn =>
        val prepare = cxn.prepare("SELECT * FROM pg_catalog.pg_attribute WHERE attrelid = $1 AND attlen = $2")
        for
          ps   <- prepare
          _    <- ps.close
          rows <- ps.query(Chunk(ValueInSql("1255"), ValueInSql(4)))
        yield rows.map(_.deepToString())

    def streamExample(pool: Pool[IO]): IO[Unit] =
      val attrelid = "1255"
      val attlen   = 4

      val query = sql"SELECT * FROM pg_catalog.pg_attribute WHERE attrelid = $attrelid" ++ sql" AND attlen = $attlen"
      println(query.render)

      pool
        .queryStream(query, 32)
        .chunks
        .map(_.map(_.deepToString()))
        .unchunks
        .compile
        .drain

    Pool
      .make[IO]:
        _.`with`(poolOptions)
          .connectingTo(connectOptions)
      .use: pool =>
        cancelExample(pool)

final case class PgAttributeRow(
  attrelid: String,
  attname: String,
  attbyval: Boolean
) derives Decoder,
      Encoder.Batch

final case class ExampleRow(
  val_json: Json
) derives Decoder,
      Encoder.Batch
