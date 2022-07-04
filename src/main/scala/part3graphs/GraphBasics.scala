package part3graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FanInShape2, Materializer, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object GraphBasics extends App {
  implicit val system: ActorSystem = ActorSystem("GraphBasics")
  implicit val mat: Materializer = Materializer(system)

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map((_: Int) + 1)
  val multiplier = Flow[Int].map((_: Int) * 10)
  val output = Sink.foreach[(Int, Int)](println)

  // step 1 - setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE data structure
      import GraphDSL.Implicits._ // brings some nice operators into scope

      // step 2 - add the necessary components of this graph
      val broadcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2)) // fan-out operator
      val zip: FanInShape2[Int, Int, (Int, Int)] = builder.add(Zip[Int, Int])                  // fan-int operator

      // step 3 - tying up the components
      input ~> broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier  ~> zip.in1

      zip.out ~> output

      // step 4 - return a closed shape
      ClosedShape
      // return a Shape object
    } // graph
  ) // runnable graph
  //  graph.run() // run the graph and materialize it

  /**
   * Exercise 1: Feed a source into 2 sinks at the same time(hint: use a broadcast)
   */

  val firstSink = Sink.foreach[Int]((x: Int) => println(s"First sink: $x"))
  val secondSink = Sink.foreach[Int]((x: Int) => println(s"Second sink: $x"))

  // step 1
  val sourceToTwoSinkGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // step 2 - declaring the components
      val broadcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))

      // step 3 - tying up the components
      input ~> broadcast ~> firstSink
               broadcast ~> secondSink // implicit port numbering!
      // broadcast.out(0) ~> firstSink
      // broadcast.out(1) ~> secondSink

      // step 4
      ClosedShape
    }
  )
  // sourceToTwoSinkGraph.run()

  /**
   * Exercise 2: Feed 2 different sources into our pipeline with a Merge and Balance components
   */
  val slowSource = input.throttle(2, 1 second)
  val fastSource = input.throttle(5, 1 second)

  val sink1 = Sink.fold[Int, Int](0)((count: Int, _: Int) => {
    println(s"Sink 1 number of elements: $count")
    count + 1
  })
  val sink2 = Sink.fold[Int, Int](0)((count: Int, _: Int) => {
    println(s"Sink 2 number of elements: $count")
    count + 1
  })

  val balanceGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
      val balance: UniformFanOutShape[Int, Int] = builder.add(Balance[Int](2))

      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge ;  balance ~> sink2

      ClosedShape
    }
  )

  balanceGraph.run()

  Thread.sleep(1000)

  system.terminate()
}
