package com.user.land

import cats.effect.*
import cats.syntax.all.*
import fs2.*
import io.circe.Json
import io.circe.syntax.*
import io.kaizensolutions.tusk.*
import io.kaizensolutions.tusk.codec.{Decoder, Encoder}
import io.kaizensolutions.tusk.interpolation.ValueInSql
import io.vertx.pgclient.PgConnectOptions as VertxPgConnectOptions
import io.vertx.sqlclient.PoolOptions as VertxPoolOptions

import scala.concurrent.duration.*
import io.kaizensolutions.tusk.codec.renamed

object Main extends IOApp.Simple:
  val run =
    val connectOptions =
      VertxPgConnectOptions()
        .setPort(5432)
        .setHost("localhost")
        .setDatabase("postgres")
        .setUser("tusk")
        .setPassword("tusk")
        .setCachePreparedStatements(true)

    val poolOptions =
      VertxPoolOptions().setMaxSize(5)

    def createExampleTable(pool: Pool[IO]): IO[Unit] =
      pool.query("CREATE TABLE IF NOT EXISTS public.example(val_json JSON)").void

    def batchExample(pool: Pool[IO]): IO[Chunk[ExampleRow]] =
      pool
        .executeBatch(
          s"""
          INSERT INTO public.example(val_json)
          VALUES ${Encoder.Batch.valuesPlaceholder[ExampleRow]}
          RETURNING *
          """,
          Chunk
            .from:
              0 to 1
            .flatMap: i =>
              Chunk(
                ExampleRow(Json.fromBoolean(true)),
                ExampleRow(Json.fromInt(42)),
                ExampleRow(Json.fromString("\"Hello,\\\" world!\"")),
                ExampleRow(
                  Json.fromValues(List(Json.fromBoolean(true), Json.fromInt(42), Json.fromString("Hello, world!")))
                ),
                ExampleRow(Json.obj(s"foo$i" := s"bar$i")),
                ExampleRow(Json.obj(s"foo$i" := s"baz$i")),
                ExampleRow(
                  Json.obj(
                    s"foo$i" := Json.obj(
                      s"qux$i" := Json.arr(
                        Json.obj(s"quux$i" := "corge$i"),
                        Json.fromBoolean(true),
                        Json.fromDoubleOrNull(1.23)
                      )
                    )
                  )
                )
              )
        )
        .indexedDecode[ExampleRow]

    def jsonExample(pool: Pool[IO]): Stream[IO, Json] =
      pool
        .queryStream(sql"SELECT val_json from public.example")
        .indexedDecode[Json]

    def cancelExample(pool: Pool[IO]): IO[Unit] =
      val dbSleep =
        pool.advanced.connection.use(_.query("select pg_sleep(10)").void >> IO.println("Sleep for 10s finished"))
      val runtimeSleep = IO.sleep(2.seconds) >> IO.println("Sleep for 2s finished")
      dbSleep.race(runtimeSleep).void

    def preparedStatementUsageExample(pool: Pool[IO]): IO[Chunk[PgAttributeRow]] =
      pool.advanced.connection.use: cxn =>
        val prepare =
          cxn.prepare("SELECT * FROM pg_catalog.pg_attribute WHERE attname like $1 AND attlen = $2")
        for
          ps <- prepare
          // _    <- ps.close
          rows <- ps.query(Chunk(ValueInSql("pro%"), ValueInSql(4)))
        yield rows.labelledDecode[PgAttributeRow]

    def streamExample(pool: Pool[IO]): IO[Unit] =
      val attlen = 4
      val query =
        sql"SELECT * FROM pg_catalog.pg_attribute WHERE attlen = $attlen"

      pool
        .queryStream(query, 32)
        .labelledDecode[PgAttributeRow]
        .debugChunks()
        .compile
        .drain

    Pool
      .make[IO]:
        _.`with`(poolOptions)
          .connectingTo(connectOptions)
      .use: pool =>
        val create = createExampleTable(pool)

        val populate = Chunk
          .from:
            0 to 10
          .parTraverse_(_ => batchExample(pool))

        val read = jsonExample(pool)
          .debugChunks(_.map(_.noSpaces).toString)
          .compile
          .drain

        create *> populate *> read

final case class PgAttributeRow(
  @renamed("attrelid") attRelId: String,
  attname: String,
  @renamed("attbyval") attByVal: Boolean
) derives Decoder,
      Encoder.Batch

final case class ExampleRow(
  val_json: Json
) derives Decoder,
      Encoder.Batch
