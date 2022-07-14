package part5advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ClosedShape, Graph, Inlet, Materializer, Outlet, Shape, SinkShape, UniformFanInShape, UniformFanOutShape}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object CustomGraphShapes {

  implicit val system: ActorSystem = ActorSystem("CustomGraphShapes")
  implicit val mat: Materializer = Materializer(system)
  import GraphDSL.Implicits._

  // balance 2x3 shape
  case class Balance2by3(in0: Inlet[Int], in1: Inlet[Int], out0: Outlet[Int], out1: Outlet[Int], out2: Outlet[Int])
    extends Shape { // Inlet[T], Outlet[T] - the ports

    override val inlets: Seq[Inlet[_]] = List(in0, in1)
    override val outlets: Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape =
      Balance2by3(
        in0.carbonCopy(), in1.carbonCopy(),
        out0.carbonCopy(), out1.carbonCopy(), out2.carbonCopy()
      )
  }

  val balance2by3Shape: Graph[Balance2by3, NotUsed] = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>

    val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
    val balance: UniformFanOutShape[Int, Int] = builder.add(Balance[Int](3))

    merge ~> balance

    Balance2by3(
      merge.in(0), merge.in(1),
      balance.out(0), balance.out(1), balance.out(2)
    )
  }

  def createSink(index: Int): Sink[Int, Future[Int]] = {
    Sink.fold(0)((count: Int, elem: Int) => {
      println(s"[sink$index] Received $elem, current count is $count")
      count + 1
    })
  }

  val balance2by3Graph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph {
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>

      val slowSource: Source[Int, NotUsed] = Source(LazyList.from(1)).throttle(1, 1 second)
      val fastSource: Source[Int, NotUsed] = Source(LazyList.from(1)).throttle(2, 1 second)

      val sinks: IndexedSeq[SinkShape[Int]] = for {
        idx: Int <- 1 to 3
      } yield builder.add(createSink(idx))

      val balance2by3: Balance2by3 = builder.add(balance2by3Shape)

      slowSource ~> balance2by3.in0
      fastSource ~> balance2by3.in1

      balance2by3.out0 ~> sinks(0)
      balance2by3.out1 ~> sinks(1)
      balance2by3.out2 ~> sinks(2)

      ClosedShape
    }
  }

  // balance2by3Graph.run()

  /**
   * Exercise: generalize the balance component, make it M x N
   */
  case class BalanceMxN[T](override val inlets: Seq[Inlet[T]], override val outlets: Seq[Outlet[T]]) extends Shape {
    override def deepCopy(): Shape = BalanceMxN(
      inlets.map((_: Inlet[T]).carbonCopy()),
      outlets.map((_: Outlet[T]).carbonCopy())
    )
  }
  object BalanceMxN {
    def apply[T](inputCount: Int, outputCount: Int): Graph[BalanceMxN[T], NotUsed] = {
      GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>

        val merge: UniformFanInShape[T, T] = builder.add(Merge[T](inputCount))
        val balance: UniformFanOutShape[T, T] = builder.add(Balance[T](outputCount))

        merge ~> balance

        BalanceMxN(
          merge.inlets.toList,
          balance.outlets.toList
        )
      }
    }
  }

  val balanceMxNGraph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph {
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>

      val slowSource: Source[Int, NotUsed] = Source(LazyList.from(1)).throttle(1, 1 second)
      val fastSource: Source[Int, NotUsed] = Source(LazyList.from(1)).throttle(3, 1 second)

      val sinks: IndexedSeq[SinkShape[Int]] = for {
        idx: Int <- 1 to 6
      } yield builder.add(createSink(idx))

      val balanceMxN: BalanceMxN[Int] = builder.add(BalanceMxN(2,6))

      slowSource ~> balanceMxN.inlets.head
      fastSource ~> balanceMxN.inlets(1)

      balanceMxN.outlets.head ~> sinks(0)
      balanceMxN.outlets(1) ~> sinks(1)
      balanceMxN.outlets(2) ~> sinks(2)
      balanceMxN.outlets(3) ~> sinks(3)
      balanceMxN.outlets(4) ~> sinks(4)
      balanceMxN.outlets(5) ~> sinks(5)
      ClosedShape
    }
  }

  def main(args: Array[String]): Unit = {
    balanceMxNGraph.run()
  }
}
