package part4techniques

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object AdvancedBackpressure {

  implicit val system: ActorSystem = ActorSystem("AdvancedBackpressure")
  implicit val mat: Materializer = Materializer(system)

  // control backpressure
  val controlledFlow: Flow[Int, Int, NotUsed] =
    Flow[Int].map((_: Int) * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstance: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events: List[PagerEvent] = List(
    PagerEvent("Service discovery failed", new Date),
    PagerEvent("Illegal elements in the data pipeline", new Date),
    PagerEvent("Number of HTTP 500 spiked", new Date),
    PagerEvent("A service stopped responding", new Date)
  )
  val eventSource: Source[PagerEvent, NotUsed] = Source(events)
  val onCallEngineer = "ken@gmail.com"

  def sendEmail(notification: Notification): Unit =
    println(s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}.")

  val notificationSink: Sink[PagerEvent, NotUsed] =
    Flow[PagerEvent]
      .map((event: PagerEvent) => Notification(onCallEngineer, event))
      .to(Sink.foreach[Notification](sendEmail))

  // standard
  // eventSource.to(notificationSink).run()
  // timer-based sources DO NOT respond to backpressure

  def sendEmailSlow(notification: Notification): Unit = {
    // simulate time-consuming tasks
    Thread.sleep(1000)

    // actually send an email
    sendEmail(notification)
  }

  // Solution: conflate
  val aggregateNotificationFlow: Flow[PagerEvent, Notification, NotUsed] = Flow[PagerEvent].conflate((event1: PagerEvent, event2: PagerEvent) => {
    val nInstances: Int = event1.nInstance + event2.nInstance
    PagerEvent(s"You have $nInstances events that require your attention", new Date, nInstances)
  }).map((resultingEvent: PagerEvent) => Notification(onCallEngineer, resultingEvent))

  // Alternative to backpressure:
  //
  //  eventSource
  //    .via(aggregateNotificationFlow).async
  //    .to(Sink.foreach[Notification](sendEmailSlow))
  //    .run()

  // Slow producers: extrapolate/expand
  val slowCounter: Source[Int, NotUsed] = Source(LazyList.from(1)).throttle(1, 1 second)
  val hungrySink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  val extrapolateFlow: Flow[Int, Int, NotUsed] = Flow[Int].extrapolate((elem: Int) => Iterator.from(elem))
  val repeater: Flow[Int, Int, NotUsed] = Flow[Int].extrapolate((elem: Int) => Iterator.continually(elem))
  val expander: Flow[Int, Int, NotUsed] = Flow[Int].expand((elem: Int) => Iterator.from(elem))

  def main(args: Array[String]): Unit = {
    slowCounter.via(expander).to(hungrySink).run()
  }
}
