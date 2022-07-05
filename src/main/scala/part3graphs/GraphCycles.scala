package part3graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FanInShape2, FlowShape, Materializer, OverflowStrategy, SinkShape, SourceShape, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip}

object GraphCycles extends App {

  implicit val system: ActorSystem = ActorSystem("GraphCycles")
  implicit val mat: Materializer = Materializer(system)

  val accelerator = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val sourceShape: SourceShape[Int] = builder.add(Source(1 to 100))
    val mergeShape: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
    val incrementerShape: FlowShape[Int, Int] = builder.add(Flow[Int].map { x: Int =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
                   mergeShape <~ incrementerShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(accelerator).run()
  // graph cycle deadlock!

  /**
   * Solution 1: MergePreferred
   */
  val actualAccelerator = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val sourceShape: SourceShape[Int] = builder.add(Source(1 to 100))
    val mergeShape: MergePreferred.MergePreferredShape[Int] = builder.add(MergePreferred[Int](1))
    val incrementerShape: FlowShape[Int, Int] = builder.add(Flow[Int].map { x: Int =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
         mergeShape.preferred <~ incrementerShape

    ClosedShape
  }
  // RunnableGraph.fromGraph(actualAccelerator).run()

  /**
   * Solution 2: buffers
   */
  val bufferedAccelerator = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val sourceShape: SourceShape[Int] = builder.add(Source(1 to 100))
    val mergeShape: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
    val repeaterShape: FlowShape[Int, Int] = builder.add(Flow[Int]
      .buffer(10, OverflowStrategy.dropHead)
      .map { x: Int =>
        println(s"Accelerating $x")
        Thread.sleep(1000)
        x
      })

    sourceShape ~> mergeShape ~> repeaterShape
                   mergeShape <~ repeaterShape

    ClosedShape
  }
  // RunnableGraph.fromGraph(bufferedAccelerator).run()

  /**
   * Cycles risk deadlocking
   * - add bounds to the number of elements in the cycle
   *
   * boundedness vs life => trade-off
   */

  /**
   * Challenge: create a fan-in shape
   * - two inputs which will be fed with EXACTLY ONE number
   * - output will emit an INFINITE FIBONACCI SEQUENCE based off those 2 numbers
   *
   * Hint: Use ZipWith and cycles, MergePreferred
   */
  val fibonacciStaticGraph = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val zipShape: FanInShape2[BigInt, BigInt, (BigInt, BigInt)] = builder.add(Zip[BigInt, BigInt])
    val mergePreferredShape: MergePreferred.MergePreferredShape[(BigInt, BigInt)] = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val fibonacciFlowShape: FlowShape[(BigInt, BigInt), (BigInt, BigInt)] = builder.add(Flow[(BigInt, BigInt)].map{
      case (last: BigInt, previous: BigInt) =>
        Thread.sleep(500)
        (last + previous, last)
    })
    val broadcastShape: UniformFanOutShape[(BigInt, BigInt), (BigInt, BigInt)] = builder.add(Broadcast[(BigInt, BigInt)](2))
    val extractLastShape: FlowShape[(BigInt, BigInt), BigInt] = builder.add(Flow[(BigInt, BigInt)].map((_: (BigInt, BigInt))._1))

    zipShape.out ~> mergePreferredShape ~> fibonacciFlowShape ~> broadcastShape ~> extractLastShape
                    mergePreferredShape.preferred             <~ broadcastShape

    UniformFanInShape(extractLastShape.out, zipShape.in0, zipShape.in1)
  }

  val fibonacciGenerator = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val source1Shape: SourceShape[BigInt] = builder.add(Source.single[BigInt](1))
      val source2Shape: SourceShape[BigInt] = builder.add(Source.single[BigInt](1))
      val sinkShape: SinkShape[BigInt] = builder.add(Sink.foreach[BigInt](println))
      val fibonacciShape: UniformFanInShape[BigInt, BigInt] = builder.add(fibonacciStaticGraph)

      source1Shape ~> fibonacciShape.in(0)
      source2Shape ~> fibonacciShape.in(1)
      fibonacciShape.out ~> sinkShape

      ClosedShape
    }
  )
  fibonacciGenerator.run()

  Thread.sleep(5000)
  system.terminate()
}
