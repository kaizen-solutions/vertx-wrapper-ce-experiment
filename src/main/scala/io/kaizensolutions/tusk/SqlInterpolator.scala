package io.kaizensolutions.tusk

import io.kaizensolutions.tusk.interpolation.*
import fs2.Chunk

extension (sc: StringContext)
  def sql(sqlValues: ValueInSql*): SqlInterpolatedString = 
    import SqlInterpolated.*

    val strings = sc.parts.iterator
    val values = sqlValues.iterator
    val resultBuilder = Array.newBuilder[SqlInterpolated]

    while strings.hasNext do  
        if values.hasNext then resultBuilder += Pair(strings.next(), values.next())
        else resultBuilder += Plain(strings.next())
    
    while values.hasNext do
        resultBuilder += Value(values.next())

    SqlInterpolatedString(Chunk.array(resultBuilder.result()))
