package com.github.ldaniels528.trifecta.ui.models

/**
  * Sampling Cursor
  * @author lawrence.daniels@gmail.com
  */
case class SamplingCursor(topic: String, offsets: Seq[SamplingCursorOffsets])
