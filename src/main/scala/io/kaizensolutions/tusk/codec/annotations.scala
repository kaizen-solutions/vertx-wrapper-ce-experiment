package io.kaizensolutions.tusk.codec

import scala.annotation.Annotation as ScalaAnnotation

final case class renamed(newName: String) extends ScalaAnnotation
