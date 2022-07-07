package part4techniques

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Status}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps

object IntegratingWithActors {

  implicit val system: ActorSystem = ActorSystem("IntegratingWithActors")
  implicit val mat: Materializer = Materializer(system)

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a string: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a number: $n")
        sender() ! (2 * n)
      case _ => ()
    }
  }

  val simpleActor: ActorRef = system.actorOf(Props[SimpleActor], "simpleActor")
  val numbersSource: Source[Int, NotUsed] = Source(1 to 10)

  // actor as a flow
  implicit val timeout: Timeout = Timeout(2 seconds)
  val actorBasedFlow: Flow[Int, Int, NotUsed] = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

  //  numbersSource.via(actorBasedFlow).to(Sink.ignore).run()
  //  numbersSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore).run()

  /**
   * Actor as a source
   */
    val actorPoweredSource: Source[Int, ActorRef] = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
        val materializedActorRef: ActorRef =
          actorPoweredSource
            .to(Sink.foreach[Int]((num: Int) =>
                println(s"Actor powered flow got number: $num")
              ))
                .run()

    materializedActorRef ! 10
    // terminating the stream
    materializedActorRef ! Status.Success("complete")

  /**
   * Actor as a destination/sink
   * - an init message
   * - an ack message to confirm reception
   * - a function to generate a message in case the stream throws an exception
   */
  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream initialized!")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream complete")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"Stream failed: $ex")
      case message =>
        log.info(s"Message: $message - has come to its final resting point.")
        sender() ! StreamAck
    }
  }

  val destinationActor: ActorRef = system.actorOf(Props[DestinationActor], "destinationActor")

  val actorPoweredSink: Sink[Int, NotUsed] = Sink.actorRefWithBackpressure[Int](
    destinationActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    ackMessage = StreamAck,
    onFailureMessage = (throwable: Throwable) => StreamFail(throwable) // optional
  )

  // Sink.actorRef() not recommended, as it does not provide BACKPRESSURE

  def main(args: Array[String]): Unit = {
    Source(1 to 10).to(actorPoweredSink).run()

    Thread.sleep(1000)
    system.terminate()
  }
}
