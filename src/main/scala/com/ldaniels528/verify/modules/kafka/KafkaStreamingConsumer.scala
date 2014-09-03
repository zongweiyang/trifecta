package com.ldaniels528.verify.modules.kafka

import akka.actor.ActorRef
import com.ldaniels528.verify.io.EndPoint
import com.ldaniels528.verify.modules.kafka.KafkaStreamingConsumer.{StreamedMessage, StreamingMessageObserver}
import com.ldaniels528.verify.util.VxUtils._
import kafka.consumer.{Consumer, ConsumerConfig}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
 * High-Level Kafka Consumer
 * @author Lawrence Daniels <lawrence.daniels@gmail.com>
 */
class KafkaStreamingConsumer(consumerConfig: ConsumerConfig) {
  private val consumer = Consumer.create(consumerConfig)

  /**
   * Closes the consumer's connection
   */
  def close(): Unit = consumer.shutdown()

  /**
   * Iterates over messages from a Kafka topic
   * @param topic the given topic name
   * @param parallelism the given number of processing threads
   */
  def iterate(topic: String, parallelism: Int): Iterator[StreamedMessage] = {
    val streamMap = consumer.createMessageStreams(Map(topic -> parallelism))
    val streams = streamMap.getOrElse(topic, Nil) map (_.iterator())
    new Iterator[StreamedMessage] {
      override def hasNext: Boolean = streams.exists(_.hasNext())

      override def next(): StreamedMessage = {
        (streams.find(_.hasNext()) map { stream =>
          val mam = stream.next()
          StreamedMessage(mam.topic, mam.partition, mam.offset, mam.key(), mam.message())
        }).getOrElse(throw new IllegalStateException("Unexpected end of stream"))
      }
    }
  }

  /**
   * Streams messages from a Kafka topic to an observer
   * @param topic the given topic name
   * @param parallelism the given number of processing threads
   * @param listener the observer to callback upon receipt of a new message
   */
  def observe(topic: String, parallelism: Int, listener: StreamingMessageObserver)(implicit ec: ExecutionContext) {
    val streamMap = consumer.createMessageStreams(Map(topic -> parallelism))

    // now create an object to consume the messages
    streamMap.get(topic) foreach { streams =>
      streams foreach { stream =>
        Future {
          val it = stream.iterator()
          while (it.hasNext()) {
            val mam = it.next()
            listener.consume(StreamedMessage(mam.topic, mam.partition, mam.offset, mam.key(), mam.message()))
          }
        }
      }
    }
  }

  /**
   * Scans a Kafka topic for the first occurrence of a message containing the given key
   * @param topic the given topic name
   * @param parallelism the given number of processing threads
   * @param key the given message key
   * @return a promise of an option of a streamed message
   */
  def scan(topic: String, parallelism: Int, key: Array[Byte])(implicit ec: ExecutionContext): Future[Option[StreamedMessage]] = {
    val streamMap = consumer.createMessageStreams(Map(topic -> parallelism))
    var completed: Boolean = false
    val promise = Promise[Option[StreamedMessage]]()

    // now create an object to consume the messages
    val tasks = (streamMap.get(topic) map { streams =>
      (streams map { stream =>
        Future {
          val it = stream.iterator()
          while (!completed && it.hasNext()) {
            val mam = it.next()
            if (mam.key sameElements key) {
              promise.success(Option(StreamedMessage(mam.topic, mam.partition, mam.offset, mam.key(), mam.message())))
              completed = true
            }
          }
        }
      }).toSeq
    }).toSeq

    // check for the failure to find the message by key
    Future.sequence(tasks.flatten).onComplete {
      case Success(v) => if (!completed) promise.success(None)
      case Failure(e) => promise.failure(e)
    }

    promise.future
  }

  /**
   * Streams data from a Kafka topic to an Akka actor
   * @param topic the given topic name
   * @param parallelism the given number of processing threads
   * @param actor the given actor reference
   */
  def stream(topic: String, parallelism: Int, actor: ActorRef)(implicit ec: ExecutionContext) {
    val streamMap = consumer.createMessageStreams(Map(topic -> parallelism))

    // now create an object to consume the messages
    streamMap.get(topic) foreach { streams =>
      streams foreach { stream =>
        Future {
          val it = stream.iterator()
          while (it.hasNext()) {
            val mam = it.next()
            actor ! StreamedMessage(mam.topic, mam.partition, mam.offset, mam.key(), mam.message())
          }
        }
      }
    }
  }

}

/**
 * Kafka Streaming Consumer Companion Object
 * @author Lawrence Daniels <lawrence.daniels@gmail.com>
 */
object KafkaStreamingConsumer {

  /**
   * Convenience method   for creating Streaming Consumer instances
   * @param zkEndPoint the given Zookeeper endpoint
   * @param groupId the given consumer group ID
   * @return a new Streaming Consumer instance
   * @see http://kafka.apache.org/07/configuration.html
   */
  def apply(zkEndPoint: EndPoint, groupId: String, params: (String, Any)*): KafkaStreamingConsumer = {
    val props = Map(
      "zookeeper.connect" -> zkEndPoint.toString,
      "group.id" -> groupId,
      "zookeeper.session.timeout.ms" -> "400",
      "zookeeper.sync.time.ms" -> "200",
      "auto.commit.interval.ms" -> "1000") ++ Map(params.map { case (k, v) => (k, String.valueOf(v))}: _*)
    new KafkaStreamingConsumer(new ConsumerConfig(props.toProps))
  }

  /**
   * Represents a stream message
   */
  case class StreamedMessage(topic: String, partition: Int, offset: Long, key: Array[Byte], message: Array[Byte])

  /**
   * This trait is implemented by classes that are interested in
   * consuming streaming Kafka messages.
   */
  trait StreamingMessageObserver {

    /**
     * Called when data is ready to be consumed
     * @param message the message as a binary string
     */
    def consume(message: StreamedMessage): Unit

  }

}