package part4techniques

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, Materializer, RestartSettings}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

object FaultTolerance {

  implicit val system: ActorSystem = ActorSystem("FaultTolerance")
  implicit val mat: Materializer = Materializer(system)

  /**
   * 1 - logging
   */
  val faultySource: Source[Int, NotUsed] = Source(1 to 10).map((e: Int) =>
    if (e == 6) throw new RuntimeException
    else e
  )
  // faultySource.log("trackingElements").to(Sink.ignore).run()
  // Upstream operators will be cancelled while Downstream operators will be informed

  /**
   * 2 - gracefully terminating a stream
   */
  //  faultySource.recover {
  //    case _: RuntimeException => Int.MinValue
  //  }.log("gracefulSource").to(Sink.ignore).run()

  /**
   * 3 - recover with another stream
   */
  //  faultySource.recoverWithRetries(attempts = 3, {
  //    case _: RuntimeException => Source(90 to 99)
  //  }).log("recoverWithRetries").to(Sink.ignore).run()

  /**
   * 4 - backoff supervision
   */
  val restartSettings: RestartSettings =
    RestartSettings(
      minBackoff = 1 second,
      maxBackoff = 30 second,
      randomFactor = 0.2 // provide multiple resources
    )

  val restartSource: Source[Int, NotUsed] =
    RestartSource.onFailuresWithBackoff(restartSettings)(() => { // generator function
      val randomNumber: Int = new Random().nextInt(20)
      Source(1 to 10).map((elem: Int) =>
        if (elem == randomNumber) throw new RuntimeException
        else elem
      )
    })
  // restartSource.log("restartBackoff").to(Sink.ignore).run()

  /**
   * 5 - supervision strategy
   */
  val numbers: Source[Int, NotUsed] = Source(1 to 20).map((n: Int) =>
    if (n == 13) throw new RuntimeException("bad luck")
    else n
  ).log("supervision")
  val supervisedNumbers: Source[Int, NotUsed] =
    numbers.withAttributes(ActorAttributes.supervisionStrategy {
      // Resume: skips the faulty element
      // Stop: stop the stream
      // Restart: resume + clears internal state
      case _: RuntimeException => Resume
      case _ => Stop
    })


  def main(args: Array[String]): Unit = {
    supervisedNumbers.to(Sink.ignore).run()

    Thread.sleep(1000)
    system.terminate()
  }
}
