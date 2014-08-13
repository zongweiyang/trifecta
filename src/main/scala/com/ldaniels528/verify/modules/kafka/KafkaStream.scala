package com.ldaniels528.verify.modules.kafka

import com.ldaniels528.verify.io.{Compression, EndPoint}
import com.ldaniels528.verify.util.VerifyUtils._

import scala.concurrent.{ExecutionContext, future}

/**
 * Verify Kafka Message Streamer
 * @author lawrence.daniels@gmail.com
 */
class KafkaStream(zkEndPoint: EndPoint, groupId: String) extends Compression {

  import kafka.consumer._

  // create the consumer instance
  private val consumer = Consumer.create(createConsumerConfig(zkEndPoint, groupId))

  /**
   * Streams data from a Kafka source
   * @param topic the given topic name
   * @param numThreads the given number of processing threads
   */
  def stream(topic: String, numThreads: Int, listener: MessageConsumer)(implicit ec: ExecutionContext) {
    val streamMap = consumer.createMessageStreams(Map(topic -> numThreads))

    // now create an object to consume the messages
    streamMap.get(topic) foreach { streams =>
      streams foreach { stream =>
        future {
          val it = stream.iterator()
          while (it.hasNext()) {
            val mam = it.next
            listener.consume(mam.offset, mam.message)
          }
        }
      }
    }
  }

  private def createConsumerConfig(zkEndPoint: EndPoint, groupId: String): ConsumerConfig = {
    new ConsumerConfig(
      Map("zookeeper.connect" -> zkEndPoint.host,
        "group.id" -> groupId,
        "zookeeper.session.timeout.ms" -> "400",
        "zookeeper.sync.time.ms" -> "200",
        "auto.commit.interval.ms" -> "1000").toProps)
  }

}