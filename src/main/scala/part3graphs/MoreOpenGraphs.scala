package part3graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FanInShape2, FanOutShape2, FlowShape, Materializer, UniformFanInShape, UniformFanOutShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

import java.util.Date

object MoreOpenGraphs extends App {

  implicit val system: ActorSystem = ActorSystem("MoreOpenGraphs")
  implicit val mat: Materializer = Materializer(system)

  /**
   * Example: Max3 operator
   * - 3 inputs of type int
   * - the maximum of the 3
   */
  val max3StaticGraph = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val max1: FanInShape2[Int, Int, Int] = builder.add(ZipWith[Int, Int, Int](Math.max(_: Int, _: Int)))
    val max2: FanInShape2[Int, Int, Int] = builder.add(ZipWith[Int, Int, Int](Math.max(_: Int, _: Int)))

    max1.out ~> max2.in0

    // (outlet, inlet-1, inlet-1, ... )
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source(1 to 10).map((_: Int) => 5)
  val source3 = Source((1 to 10).reverse)

  val max3Sink = Sink.foreach[Int]((x: Int) => println(s"Max is: $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val max3Shape: UniformFanInShape[Int, Int] = builder.add(max3StaticGraph)

      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)
      max3Shape.out ~> max3Sink

      ClosedShape
    }
  )
  //  max3RunnableGraph.run()

  // same for UniformFanOutShape

  /**
   * Non-uniform fan out shape
   *
   * Processing bank transactions
   * - Txn suspicious if amount > 10,000
   *
   * Streams component for Transactions
   * - output1: let the transaction go through
   * - output2: suspicious transaction identities
   */
  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source(List(
    Transaction("5273890572", "Paul", "Jim", 100, new Date),
    Transaction("3578902532", "Daniel", "Jim", 100000, new Date),
    Transaction("5489036033", "Jim", "Alice", 7000, new Date)
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String]((transactionId: String) => println(s"Suspicious transaction ID: $transactionId"))

  // step 1
  val suspiciousTransactionStaticGraph = GraphDSL.create() {implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val broadcast: UniformFanOutShape[Transaction, Transaction] = builder.add(Broadcast[Transaction](2))
    val suspiciousTransactionFilter: FlowShape[Transaction, Transaction] = builder.add(Flow[Transaction].filter((_: Transaction).amount > 10000))
    val transactionIdExtractor: FlowShape[Transaction, String] = builder.add(Flow[Transaction].map[String]((_: Transaction).id))

    broadcast.out(0) ~> suspiciousTransactionFilter ~> transactionIdExtractor

    new FanOutShape2(broadcast.in, broadcast.out(1), transactionIdExtractor.out)
  }

  val suspiciousTransactionAnalysisRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() {implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val suspiciousTransactionShape: FanOutShape2[Transaction, Transaction, String] = builder.add(suspiciousTransactionStaticGraph)

      transactionSource ~> suspiciousTransactionShape.in
      suspiciousTransactionShape.out0 ~> bankProcessor
      suspiciousTransactionShape.out1 ~> suspiciousAnalysisService

      ClosedShape
    }
  )

  suspiciousTransactionAnalysisRunnableGraph.run()
}
