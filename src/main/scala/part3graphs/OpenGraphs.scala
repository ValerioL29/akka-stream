package part3graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}
import akka.stream._

object OpenGraphs extends App {

  implicit val system: ActorSystem = ActorSystem("OpenGraphs")
  implicit val mat: Materializer = Materializer(system)

  /**
   * A composite source that concatenates 2 sources
   * - emits ALL the elements from the first source
   * - then ALL the elements from the second
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  // step 1
  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // step 2 - declaring components
      val concat: UniformFanInShape[Int, Int] = builder.add(Concat[Int](2))

      // step 3 - typing them together
      firstSource ~> concat
      secondSource ~> concat

      // step 4
      SourceShape(concat.out)
    }
  )
  //  sourceGraph
  //    .to(Sink.foreach[Int](println))
  //    .run()

  /**
   * Complex sink
   */
  val sink1 = Sink.foreach[Int]((x: Int) => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int]((x: Int) => println(s"Meaningful thing 2: $x"))

  // step 1
  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // step 2 - add a broadcast
      val broadcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))

      // step 3 - tie components together
      broadcast ~> sink1
      broadcast ~> sink2

      // step 4 - return the shape
      SinkShape(broadcast.in)
    }
  )

  //  firstSource
  //    .to(sinkGraph)
  //    .run()

  /**
   * Challenge - complex flow?
   * Write your own flow that's composed of two other flows
   * - one that adds 1 to a number
   * - one that does number * 10
   */
  val incrementer = Flow[Int].map((_: Int) + 1)
  val multiplier = Flow[Int].map((_: Int) * 10)

  // step 1
  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // everything operates on SHAPES
      // step 2 - define auxiliary SHAPES
      val incrementerShape: FlowShape[Int, Int] = builder.add(incrementer)
      val multiplierShape: FlowShape[Int, Int] = builder.add(multiplier)
      // step 3 - connect the components
      incrementerShape ~> multiplierShape

      // step 4 - form the shape!
      FlowShape(incrementerShape.in, multiplierShape.out)
    } // static graph
  ) // component

  //  firstSource
  //    .via(flowGraph)
  //    .to(Sink.foreach[Int](println))
  //    .run()

  /**
   * Exercise: flow from a sink and a source
   */
  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
        val sinkShape: SinkShape[A] = builder.add(sink)
        val sourceShape: SourceShape[B] = builder.add(source)
        FlowShape(sinkShape.in, sourceShape.out)
      }
    )

  // can send Termination signal and Backpressure signal
  val f = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source(1 to 10))
}
