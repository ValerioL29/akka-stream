package playground

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import scala.concurrent.Future
import scala.util.Try

object Playground extends App {
  implicit val system: ActorSystem = ActorSystem("Playground")
  implicit val mat: Materializer = Materializer(system)
  import system.dispatcher

  val source = Source(1 to 10)
  val flow = Flow[Int].map((x: Int) => {
    println(x)
    x
  })
  val sink = Sink.fold[Int, Int](0)((_: Int) + (_: Int))

  // connect to the Source to the Sink, obtaining a RunnableGraph
  val runnable: RunnableGraph[Future[Int]] = source.via(flow).toMat(sink)(Keep.right)

  // materialize the flow and get the value of the FoldSink
  val sum: Future[Int] = runnable.run()
  sum.onComplete((x: Try[Int]) => println(s"Sum: $x"))

  Thread.sleep(1000)
  system.terminate()
}
