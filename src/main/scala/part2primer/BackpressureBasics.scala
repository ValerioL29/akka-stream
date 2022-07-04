package part2primer

import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.language.postfixOps

object BackpressureBasics extends App {
  implicit val system: ActorSystem = ActorSystem("BackpressureBasics")
  implicit val mat: Materializer = Materializer(system)

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x: Int =>
    // simulate a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  // fastSource.to(slowSink).run() // fusing?!
  // not backpressure

  // fastSource.async.to(slowSink).run()
  // backpressure!

  val simpleFlow = Flow[Int].map { x: Int =>
    println(s"Incoming: $x")
    x + 1
  }

  // Internal buffer is 16
  // Buffering is one kind of Backpressure
  //  fastSource.async
  //    .via(simpleFlow).async
  //    .to(slowSink)
  //    .run()

  /**
   * Reactions to backpressure (in order):
   * - try to slow down if possible
   * - buffer elements until there's more demand
   * - drop down elements from the buffer if it overflows
   * - tear down/kill the whole stream (failure)
   */
  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
  fastSource.async
    .via(bufferedFlow).async
    .to(slowSink)
    .run()

  //  1 - 16: nobody is effected by Backpressure
  //  17 - 26: flow will buffer, flow will start dropping at the next element
  //  26 - 1000: flow will always drop the oldest elements
  //     => 991 - 1000
  //     => 992 - 1001
  //     => sink

  /**
   * Overflow strategies:
   * - drop head = oldest
   * - drop tail = newest
   * - drop new = exact element to be added = keeps the buffer
   * - drop the entire buffer
   * - backpressure signal
   * - fail
   */

  // throttling
  import scala.concurrent.duration._
  fastSource.throttle(10, 1 second).runForeach(println)
}
