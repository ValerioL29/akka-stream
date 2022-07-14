package part5advanced

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source, SubFlow}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object SubStreams {

  implicit val system: ActorSystem = ActorSystem("SubStreams")
  implicit val mat: Materializer = Materializer(system)
  import system.dispatcher

  // 1 - grouping a stream by a certain function
  val wordSource: Source[String, NotUsed] = Source(List(
    "AKKA", "is", "amazing", "learning", "sub-streams"
  ))
  val groups: SubFlow[String, NotUsed, wordSource.Repr, RunnableGraph[NotUsed]] =
    wordSource.groupBy(30, { word: String =>
      if (word.isEmpty) '\u0000'
      else word.toLowerCase().charAt(0)
    })

  val groupGraph: RunnableGraph[NotUsed] =
    groups.to(Sink.fold(0) { (count: Int, word: String) =>
      val newCount: Int = count + 1
      println(s"I just received $word, count is $newCount")
      newCount
    })

  // 2 - merge sub-streams back
  val textSource: Source[String, NotUsed] = Source(List(
    "I love AKKA Stream",
    "this is amazing",
    "learning from Rock The JVM"
  ))

  val totalCharacterCountGraph: RunnableGraph[Future[Int]] =
    textSource.groupBy(2, (str: String) => str.length % 2)
      .map((_: String).length) // do your expensive computation here since it is done by parallel
      .mergeSubstreamsWithParallelism(2) // parallelism specifies the number of sub-streams we just set up
      .toMat(Sink.reduce[Int]((_: Int) + (_: Int)))(Keep.right)

  //  val counterFuture = totalCharacterCountGraph.run()
  //
  //  counterFuture.onComplete {
  //    case Success(value) => println(s"Total char count: $value")
  //    case Failure(exception) => println(s"Char computation failed: $exception")
  //  }

  // 3 - splitting a stream into sub-streams, when a condition is met
  val text: String =
    "I love AKKA Stream\n" +
    "this is amazing\n" +
    "learning from Rock The JVM\n"

  val anotherCharCountGraph: RunnableGraph[Future[Int]] =
    Source(text.toList)
      .splitWhen((c: Char) => c == '\n') // form a new sub-stream when met with the predicate
      .filter((_: Char) != 'n')
      .map((_: Char) => 1)
      .mergeSubstreams
      .toMat(Sink.reduce[Int]((_: Int) + (_: Int)))(Keep.right)

  //  val anotherCharCountFuture: Future[Int] = anotherCharCountGraph.run()
  //
  //  anotherCharCountFuture.onComplete {
  //    case Success(value) => println(s"Total char count alternative: $value")
  //    case Failure(exception) => println(s"Char computation failed: $exception")
  //  }

  // 4 - flatten: concatenate & merge => flatMap
  val simpleSource: Source[Int, NotUsed] = Source(1 to 5)

  def main(args: Array[String]): Unit = {
    val flatConcatFuture: Future[Done] = simpleSource.log("flatMap-Concat")
      .flatMapConcat((x: Int) => Source(x to (3 * x)))
      .runForeach(println)

    flatConcatFuture.onComplete {
      case Success(_) => println(s"Concat flatten finished.")
      case Failure(exception) => println(s"Failed: $exception")
    }

    val flatMergeFuture: Future[Done] = simpleSource.log("flatMap-Merge")
      .flatMapMerge(2, (x: Int) => Source(x to (3 * x)))
      .runForeach(println)
    
    flatMergeFuture.onComplete {
      case Success(_) => println(s"Merge flatten finished.")
      case Failure(exception) => println(s"Failed: $exception")
    }
  }
}
