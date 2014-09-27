package com.ldaniels528.trifecta.modules.core

import java.io.{DataOutputStream, FileOutputStream}

import com.ldaniels528.trifecta.modules.io.OutputWriter

import scala.concurrent.ExecutionContext

/**
 * File Output Writer
 * @author Lawrence Daniels <lawrence.daniels@gmail.com>
 */
class FileOutputWriter(path: String) extends OutputWriter {
  private val out = new DataOutputStream(new FileOutputStream(path))

  override def write(key: Array[Byte], message: Array[Byte])(implicit ec: ExecutionContext) = {
    // persist the key
    out.writeInt(key.length)
    out.write(key)

    // persist the message
    out.writeInt(message.length)
    out.write(message)
  }

  override def close() = out.close()

}
