package part5advanced

import akka.{Done, NotUsed}
import akka.actor.{ActorSystem, Cancellable}
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, RunnableGraph, Sink, Source}
import akka.stream.{FlowShape, Graph, KillSwitches, Materializer, SharedKillSwitch, UniqueKillSwitch}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object DynamicStreamHandling {

  implicit val system: ActorSystem = ActorSystem("DynamicStreamHandling")
  implicit val mat: Materializer = Materializer(system)
  import system.dispatcher

  // #1: Kill Switch
  val killSwitchFlow: Graph[FlowShape[Int, Int], UniqueKillSwitch] = KillSwitches.single[Int] // kill a single stream
  val counter: Source[Int, NotUsed] = Source(LazyList.from(1)).throttle(1, 1 second).log("counter")
  val sink: Sink[Any, Future[Done]] = Sink.ignore

  val killSwitch: RunnableGraph[UniqueKillSwitch] = counter
    .viaMat(killSwitchFlow)(Keep.right)
    .to(sink)
  //    .run()

  //    system.scheduler.scheduleOnce(3 seconds) {
  //      killSwitch.shutdown()
  //    }

  // shared kill switch
  val anotherCounter: Source[Int, NotUsed] =
    Source(LazyList.from(1)).throttle(2, 1 second).log("anotherCounter")
  val sharedKillSwitch: SharedKillSwitch =
    KillSwitches.shared("oneButtonToRuleThemAll")

  //  counter.via(sharedKillSwitch.flow).runWith(sink)
  //  anotherCounter.via(sharedKillSwitch.flow).runWith(sink)
  //
  //  system.scheduler.scheduleOnce(3 seconds) {
  //    sharedKillSwitch.shutdown()
  //  }

  // #2: MergeHub
  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] =
    MergeHub.source[Int]
  //  val materializedSink: Sink[Int, NotUsed] =
  //    dynamicMerge
  //      .to(Sink.foreach[Int](println))
  //      .run()
  //  Source(1 to 10).throttle(1, 1 second).log("mergeSourceWithCounter").runWith(materializedSink)
  //  counter.runWith(materializedSink)

  // #3: BroadcastHub
  // A simple producer that publishes a new "message" every second
  val producer: Source[String, Cancellable] = Source.tick(1.second, 1.second, "New message")

  // Attach a BroadcastHub Sink to the producer. This will materialize to a
  // corresponding Source.
  // (We need to use toMat and Keep.right since by default the materialized
  // value to the left is used)
  val runnableGraph: RunnableGraph[Source[String, NotUsed]] =
  producer.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

  // By running/materializing the producer, we get back a Source, which
  // gives us access to the elements published by the producer.
  // val fromProducer: Source[String, NotUsed] = runnableGraph.run()

  // Print out messages from the producer in two independent consumers
  //  fromProducer.runForeach((msg: String) => println("consumer1: " + msg))
  //  fromProducer.runForeach((msg: String) => println("consumer2: " + msg))

  /**
   * Challenge - combine a MergeHub and a BroadcastHub.
   * - A Publisher-Subscriber component
   *
   */
  val merge: Source[String, Sink[String, NotUsed]] = MergeHub.source[String]
  val broadcast: Sink[String, Source[String, NotUsed]] = BroadcastHub.sink[String]
  val (publisherPort, subscriberPort) = merge.toMat(broadcast)(Keep.both).run()

  def main(args: Array[String]): Unit = {
    system.scheduler.scheduleOnce(1 second) {
      subscriberPort.runWith(Sink.foreach[String]((e: String) => println(s"I have received: $e")))
      subscriberPort.map((str: String) => str.length).runWith(Sink.foreach[Int]((n: Int) => println(s"I got a number: $n")))

      Source(List("Akka", "is", "amazing")).runWith(publisherPort)
      Source(List("I", "love", "Scala")).runWith(publisherPort)
      Source.single("STREAMS").runWith(publisherPort)
    }
  }
}
