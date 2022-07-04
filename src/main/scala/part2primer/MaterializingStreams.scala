package part2primer

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}

object MaterializingStreams extends App {
  implicit val system: ActorSystem = ActorSystem("MaterializingStreams")
  implicit val mat: Materializer = Materializer(system)
  import system.dispatcher

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
  //  val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int]((a: Int, b: Int) => a + b)
  //  val sumFuture = source.runWith(sink)
  //  sumFuture.onComplete{
  //    case Success(value)     => println(s"The sum of all elements is: $value")
  //    case Failure(exception) => println(s"The sum of the elements could not be computed: $exception")
  //  }

  // choosing materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map((_: Int) + 1)
  val simpleSink = Sink.foreach[Int](println)
  val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  graph.run().onComplete{
    case Success(_)         => println("Stream processing finished.")
    case Failure(exception) => println(s"The sum of the elements could not be computed: $exception")
  }

  // sugars
  Source(1 to 10).runWith(Sink.reduce[Int]((_: Int) + (_: Int))) // source.to(Sink.reduce)(Keep.right)
  Source(1 to 10).runReduce((_: Int) + (_: Int))

  // backwards
  Sink.foreach[Int](println).runWith(Source.single(42)) // source(..).to(sink...).run()
  // both ways
  Flow[Int].map((_: Int) * 2).runWith(simpleSource, simpleSink)

  /**
   * Exercise:
   * - return the last element out of a source (use Sink.last)
   * - compute the total word count out of a stream of sentences
   * - map, fold, reduce
   */
  val f1 = Source(1 to 10).toMat(Sink.last[Int])(Keep.right).run()
  val f2 = Source(1 to 10).runWith(Sink.last[Int])

  val sentenceSource = Source(List(
    "Akka is awesome",
    "I love streams",
    "Materialized values are killing me"
  ))
  val wordCountSink = Sink.fold[Int, String](0)(
    (currentWords: Int, newSentence: String) =>
      currentWords + newSentence.split(" ").length
  )
  val g1 = sentenceSource.toMat(wordCountSink)(Keep.right).run()
  val g2 = sentenceSource.runWith(wordCountSink)
  val g3 = sentenceSource.runFold(0)(
    (currentWords: Int, newSentence: String) =>
      currentWords + newSentence.split(" ").length
  )

  val wordCountFlow = Flow[String].fold[Int](0)(
    (currentWords: Int, newSentence: String) =>
      currentWords + newSentence.split(" ").length
  )
  val g4 = sentenceSource.via(wordCountFlow).toMat(Sink.head[Int])(Keep.right).run()
  val g5 = sentenceSource.viaMat(wordCountFlow)(Keep.left).toMat(Sink.head[Int])(Keep.right).run()
  val g6 = sentenceSource.via(wordCountFlow).runWith(Sink.head[Int])
  val g7 = wordCountFlow.runWith(sentenceSource, Sink.head[Int])._2

}
