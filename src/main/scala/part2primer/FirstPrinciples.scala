package part2primer

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App{
  implicit val system: ActorSystem = ActorSystem("FirstPrinciples")
  implicit val mat: Materializer = Materializer(system)

  // sources
  val source = Source(1 to 10)
  // sinks
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)
  //  graph.run() // implicit materializer: ActorMaterializer

  // flows transform elements
  val flow = Flow[Int].map((x: Int) => x + 1)
  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

  //  sourceWithFlow.to(sink).run()
  //  source.to(flowWithSink).run()
  //  source.via(flow).to(sink).run()

  // nulls are NOT allowed
  //  val illegalSource = Source.single[String](null)
  //  illegalSource.to(Sink.foreach(println)).run()
  // use Options instead

  // various kinds of sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(LazyList(1)) // do not confuse an Akka stream with a "collection Stream"

  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.future(Future(42))

  // sinks
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves head and then closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a: Int, b: Int) => a + b)

  // flows - usually mapped to collection operators
  val mapFlow = Flow[Int].map((_: Int) * 2)
  val takeFlow = Flow[Int].take(5) // turn to a finite stream
  // drop, filter
  // NOT have flatMap or flatMap like operators

  // source -> flow -> flow -> ... -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  //  doubleFlowGraph.run()

  // syntactic sugars
  val mapSource = Source(1 to 10).map((_: Int) * 2) // Source(1 to 10).via(Flow[Int].map(x => x * 2))
  // run streams directly
  //  mapSource.runForeach(println) // mapSource.to(Sink.foreach[Int](println).run()

  /**
   * Exercise: create a stream that takes the names of persons, then you will keep the first 2 names with
   * length > 5 characters
   */
  val names: List[String] = List("Alice", "Bob", "Charlie", "David", "Martin", "AkkaStreams")
  val nameSource = Source(names)
  val longNameFlow = Flow[String].filter((_: String).length > 5)
  val limitFlow = Flow[String].take(2)
  val nameSink = Sink.foreach[String](println)

  nameSource.via(longNameFlow).via(limitFlow).to(nameSink).run()
}
