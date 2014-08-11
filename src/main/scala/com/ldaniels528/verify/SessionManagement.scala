package com.ldaniels528.verify

import java.io.File

import akka.actor._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.FiniteDuration

/**
 * Session Management
 * @author ldaniels
 */
object SessionManagement {
  private[this] lazy val logger = LoggerFactory.getLogger(getClass)
  private[this] val system = ActorSystem("SessionManagement")
  val historyActor = system.actorOf(Props[HistoryActor], name = "historyActor")

  def setupHistoryUpdates(history: History, file: File, frequency: FiniteDuration): Cancellable = {
    system.scheduler.schedule(frequency, frequency, historyActor, SaveHistory(history, file))
  }

  def shutdown() {
    system.shutdown()
  }

  /**
   * Session History Management Actor
   */
  class HistoryActor() extends Actor {
    def receive = {
      case SaveHistory(history, file) =>
        logger.info("Saving history file...")
        history.store(file)
      case unknown => unhandled(unknown)
    }
  }

  /**
   * Save History to disk message
   * @param history the given [[History]] instance
   * @param file the file for which to save the history
   */
  case class SaveHistory(history: History, file: File)

}