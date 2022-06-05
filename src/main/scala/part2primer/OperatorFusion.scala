package part2primer

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {
  implicit val system: ActorSystem = ActorSystem("OperatorFusion")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map((_: Int) + 1)
  val simpleFlow2 = Flow[Int].map((_: Int) * 10)
  val simpleSink = Sink.foreach[Int](println)

  // this runs on the SAME ACTOR
  //  simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()
  // operator/component FUSION -> By default, one actor

  // "Equivalent" behavior
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        val x2: Int = x + 1
        val y: Int = x2 * 10
        // sink operation
        println(y)
    }
  }
  val simpleActor = system.actorOf(Props[SimpleActor])
  //  (1 to 1000).foreach(simpleActor ! (_: Int))


  // complex flows
  val complexFlow = Flow[Int].map{ x: Int =>
    // simulating a long computation
    Thread.sleep(1000)
    x * 10
  }
  val complexFlow2 = Flow[Int].map{ x: Int =>
    // simulating a long computation
    Thread.sleep(1000)
    x * 10
  }

  // throughput is LOW!!!
  //  simpleSource.via(complexFlow).via(complexFlow2).to(simpleSink).run()

  // async boundary
  //  simpleSource.via(complexFlow).async // runs on one actor
  //    .via(complexFlow2).async // runs on another actor
  //    .to(simpleSink) // runs on a third actor
  //    .run()
  // DOUBLE throughput!

  // ordering guarantees
  Source(1 to 3)
    .map((element: Int) => { println(s"Flow A: $element"); element }).async
    .map((element: Int) => { println(s"Flow B: $element"); element }).async
    .map((element: Int) => { println(s"Flow C: $element"); element }).async
    .runWith(Sink.ignore)
}
