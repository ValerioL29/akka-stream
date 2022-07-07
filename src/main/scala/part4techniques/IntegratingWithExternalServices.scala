package part4techniques

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object PrivateExecutionContext {
  implicit val system: ActorSystem = ActorSystem("IntegratingWithExternalServices")
  implicit val mat: Materializer = Materializer(system)
  // import system.dispatcher - not recommended in practice for mapAsync
  implicit val dispatcher: MessageDispatcher = system.dispatchers.lookup("dedicated-dispatcher")
}

object IntegratingWithExternalServices {
  import PrivateExecutionContext._

  def genericExternalService[A, B](element: A): Future[B] = ???

  // example: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource: Source[PagerEvent, NotUsed] = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
    PagerEvent("FastDataPipeline", "Illegal elements in the data pipeline", new Date),
    PagerEvent("AkkaInfra", "A service stopped responding", new Date),
    PagerEvent("SuperFronted", "A button doesn't work", new Date),
  ))

  class PagerActor extends Actor with ActorLogging {
    private val engineers: List[String] = List("Ken", "Norman", "Yao")
    private val emails: Map[String, String] = Map(
      "Ken"    -> "ken@gmail.com",
      "Norman" -> "norman@gmail.com",
      "Yao"    -> "yao@gmail.com",
    )

    def processEvent(pagerEvent: PagerEvent): Future[String] = Future {
      val engineerIndex: Long = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer: String = engineers(engineerIndex.toInt)
      val engineerEmail: String = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      // return the email that was paged
      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

    val infraEvents: Source[PagerEvent, NotUsed] =
      eventSource.filter((_: PagerEvent).application == "AkkaInfra")
    //  val pagedEngineerEmails: Source[String, NotUsed] = infraEvents
    //    .mapAsync(parallelism = 4)((event: PagerEvent) => PagerActor.processEvent(event))

    // guarantees the relative order of elements
    val pagedEmailSink: Sink[String, Future[Done]] =
      Sink.foreach[String]((email: String) => println(s"Successfully sent notification to $email"))

  def demoPagerService(): Unit = {
    import akka.pattern.ask
    implicit val timeout: Timeout = Timeout(3 seconds)

    val pagerActor: ActorRef = system.actorOf(Props[PagerActor], "pagerActor")
    val alternativePagedEngineerEmails: Source[String, NotUsed] =
      infraEvents.mapAsync(parallelism = 4)((event: PagerEvent) =>
        (pagerActor ? event).mapTo[String]
      )

    alternativePagedEngineerEmails.to(pagedEmailSink).run()
  }

  def main(args: Array[String]): Unit = {
    demoPagerService()

    Thread.sleep(10000)
    system.terminate()
  }
}
