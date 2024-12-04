package io.kaizensolutions.tusk.interpolation

import fs2.Chunk
import io.kaizensolutions.tusk.SqlValues

private[tusk] enum SqlInterpolated:
  case Plain(value: String)
  case Value(sqlValue: ValueInSql)
  case Pair(value: String, sqlValue: ValueInSql)

opaque type SqlInterpolatedString = Chunk[SqlInterpolated]
object SqlInterpolatedString:
  private[tusk] def apply(in: Chunk[SqlInterpolated]): SqlInterpolatedString = in

  def apply(in: String): SqlInterpolatedString = Chunk.singleton(SqlInterpolated.Plain(in))

  import SqlInterpolated.*
  extension (input: SqlInterpolatedString)
    private[tusk] def escapeHatch: Chunk[SqlInterpolated] = input

    def ++(that: String): SqlInterpolatedString = input.escapeHatch ++ Chunk.singleton(Plain(that))

    def ++(that: SqlInterpolatedString): SqlInterpolatedString = input.escapeHatch ++ that.escapeHatch

    def render: (String, SqlValues) =
      var currentIndex  = 1
      val placeholder   = "$"
      val stringBuilder = new scala.collection.mutable.StringBuilder()
      val valueBuilder  = Array.newBuilder[ValueInSql]
      input.foreach:
        case Plain(value) =>
          stringBuilder.append(value)

        case Value(sqlValue) =>
          stringBuilder.append(placeholder).append(currentIndex)
          valueBuilder += sqlValue
          currentIndex += 1

        case Pair(value, sqlValue) =>
          stringBuilder.append(value)
          stringBuilder.append(placeholder).append(currentIndex)
          valueBuilder += sqlValue
          currentIndex += 1

      stringBuilder.toString() -> SqlValues(Chunk.array(valueBuilder.result()))
