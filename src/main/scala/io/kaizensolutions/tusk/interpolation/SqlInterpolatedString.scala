package io.kaizensolutions.tusk.interpolation

import fs2.Chunk

private[tusk] enum SqlInterpolated:
  case Plain(value: String)
  case Value(sqlValue: ValueInSql)
  case Pair(value: String, sqlValue: ValueInSql)

opaque type SqlInterpolatedString = Chunk[SqlInterpolated]
object SqlInterpolatedString:
  private[tusk] def apply(in: Chunk[SqlInterpolated]): SqlInterpolatedString = in

  def apply(in: String): SqlInterpolatedString = Chunk.singleton(SqlInterpolated.Plain(in))

  import SqlInterpolated.*
  extension (value: SqlInterpolatedString)
    private[tusk] def escapeHatch: Chunk[SqlInterpolated] = value

    def ++(that: String): SqlInterpolatedString = value.escapeHatch ++ Chunk.singleton(Plain(that))

    def ++(that: SqlInterpolatedString): SqlInterpolatedString = value.escapeHatch ++ that.escapeHatch

    def render: (String, Chunk[ValueInSql]) =
      var currentIndex  = 1
      val placeholder   = "$"
      val stringBuilder = new scala.collection.mutable.StringBuilder()
      val valueBuilder  = Array.newBuilder[ValueInSql]
      value.foreach:
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

      stringBuilder.toString() -> Chunk.array(valueBuilder.result())
