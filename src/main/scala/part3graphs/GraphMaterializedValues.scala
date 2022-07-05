package part3graphs

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{FlowShape, Materializer, SinkShape, UniformFanOutShape}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {

  implicit val system: ActorSystem = ActorSystem("GraphMaterializedValues")
  implicit val mat: Materializer = Materializer(system)

  val wordSource = Source(List(
    "Akka",
    "is",
    "awesome",
    "Rock",
    "the",
    "JVM"
  ))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count: Int, _: String) => count + 1)

  /**
   * A composite component (sink)
   * - prints out all strings which are lowercase
   * - COUNTS the strings that are short (< 5 chars)
   */

  val complexWordSink = Sink.fromGraph(
    GraphDSL.createGraph (printer, counter)
    ((_: Future[Done], counterMat: Future[Int]) => counterMat) {
      implicit builder: GraphDSL.Builder[Future[Int]] =>
      (printerShape: SinkShape[String], counterShape: SinkShape[String]) => {

        import GraphDSL.Implicits._

        val broadcast: UniformFanOutShape[String, String] =
          builder.add(Broadcast[String](2))

        val lowercaseFilter: FlowShape[String, String] =
          builder.add(Flow[String].filter((word: String) => word == word.toLowerCase))

        val shortStringFilter: FlowShape[String, String] =
          builder.add(Flow[String].filter((_: String).length < 5))

        broadcast ~> lowercaseFilter ~> printerShape
        broadcast ~> shortStringFilter ~> counterShape

        SinkShape(broadcast.in)
      }
    }
  )

  import system.dispatcher
  //  val shortStringCountFuture =
  //    wordSource
  //      .toMat(complexWordSink)(Keep.right)
  //      .run()
  //  shortStringCountFuture.onComplete {
  //    case Success(count) => println(s"The total number of short strings is: $count")
  //    case Failure(exception) => println(s"The total number of short strings failed: $exception")
  //  }

  /**
   * Exercise
   * Hint: use a broadcast and a Sink.fold
   */
  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink: Sink[B, Future[Int]] =
      Sink.fold[Int, B](0)((count: Int, _: B) => count + 1)

    Flow.fromGraph(
      GraphDSL.createGraph(counterSink) { implicit builder: GraphDSL.Builder[Future[Int]] =>
        (counterSinkShape: SinkShape[B]) => {
          import GraphDSL.Implicits._

          val broadcast: UniformFanOutShape[B, B] = builder.add(Broadcast[B](2))
          val originalFlowShape: FlowShape[A, B] = builder.add(flow)

          originalFlowShape ~> broadcast ~> counterSinkShape

          FlowShape(originalFlowShape.in, broadcast.out(1))
        }
      }
    )
  }

  val simpleSource = Source(1 to 42)
  val simpleFlow = Flow[Int].map((_: Int) + 1)
  val simpleSink = Sink.ignore

  val enhancedFlowCountFuture =
    simpleSource
      .viaMat(enhanceFlow(simpleFlow))(Keep.right)
      .toMat(simpleSink)(Keep.left)
      .run()
  enhancedFlowCountFuture.onComplete {
    case Success(count) => println(s"$count elements went through the enhanced flow")
    case Failure(exception) => println(s"$exception failed")
  }

  Thread.sleep(1000)
  system.terminate()
}
