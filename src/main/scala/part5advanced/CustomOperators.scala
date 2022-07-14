package part5advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

object CustomOperators {

  implicit val system: ActorSystem = ActorSystem("CustomOperators")
  implicit val mat: Materializer = Materializer(system)

  // 1 - a custom source which emits random numbers until canceled

  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {
    // step 1: define the ports and the component-specific members
    val outPort: Outlet[Int] = Outlet[Int]("randomGenerator")
    val random = new Random()

    // step 2: construct a new shape
    override def shape: SourceShape[Int] = SourceShape(outPort)
    // step 3: create the logic
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // step 4: define mutable state
      setHandler(outPort, new OutHandler {
        // when there is demand from downstream
        override def onPull(): Unit = {
          // emit a new element
          val nextNumber: Int = random.nextInt(max)
          // push it out of the outPort
          push(outPort, nextNumber)
        }
      })
    }
  }

  // create a new source with customization
  val randomGeneratorSource: Source[Int, NotUsed] = Source.fromGraph(new RandomNumberGenerator(100)).throttle(10, 1 second)
  // graph will run after it is materialized
  // randomGeneratorSource.runForeach(println)

  // 2 - custom sink which prints elements in batches of a given size

  class BatchPrinter[T](batchSize: Int) extends GraphStage[SinkShape[T]] {

    val inPort: Inlet[T] = Inlet[T]("batchPrinter")

    override def shape: SinkShape[T] = SinkShape(inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // mutable state
      val batch = new mutable.Queue[T]

      // pre-start
      override def preStart(): Unit = pull(inPort)

      setHandler(inPort, new InHandler {
        // when the upstream wants to send me an element
        override def onPush(): Unit = {
          val nextElement: T = grab(inPort)
          batch.enqueue(nextElement)

          // assume some complex computation is here
          Thread.sleep(100)

          if (batch.size >= batchSize)
            println("New batch: " + batch.dequeueAll((_: T) => true).mkString("[", ",", "]"))

          pull(inPort) // send demand upstream
        }

        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty) {
            if (batch.size >= batchSize) {
              println("New batch: " + batch.dequeueAll((_: T) => true).mkString("[", ",", "]"))
              println("Stream finished.")
            }
          }
        }
      })
    }
  }

  val batchSink: Sink[Int, NotUsed] = Sink.fromGraph(new BatchPrinter[Int](5))
  // randomGeneratorSource.to(batchSink).run()

  /**
   * Exercise: a custom flow - a simple filter flow
   * - 2 ports: an input port and an output port
   */

  class FilterFlow[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    val inPort: Inlet[T] = Inlet[T]("inputPort")
    val outPort: Outlet[T] = Outlet[T]("outputPort")

    override def shape: FlowShape[T, T] = FlowShape[T, T](inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          try {
            val nextElement: T = grab(inPort)

            if (predicate(nextElement))
              push(outPort, nextElement)
            else
              pull(inPort)
          }
          catch {
            case ex: Throwable => failStage(ex)
          }
        }

        override def onUpstreamFinish(): Unit = println("Stream finished.")
      })

      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = {
          pull(inPort)
        }
      })
    }
  }

  //  Source(1 to 10)
  //    .via(Flow.fromGraph(new FilterFlow[Int]((_: Int) % 3 == 0)))
  //    .runForeach(println)

  /**
   * Materialized values in graph stages
   */
  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {

    val inPort: Inlet[T] = Inlet[T]("counterIn")
    val outPort: Outlet[T] = Outlet[T]("counterOut")

    override def shape: FlowShape[T, T] = FlowShape[T, T](inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {

      // Finish when the stream is terminated
      val promise: Promise[Int] = Promise[Int]

      val logic: GraphStageLogic = new GraphStageLogic(shape) {
        // setting mutable state
        var counter = 0

        setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
            // extract the element
            val nextElement: T = grab(inPort)
            counter += 1
            // pass it on
            push(outPort, nextElement)
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        })

        setHandler(outPort, new OutHandler {
          override def onPull(): Unit = pull(inPort)

          override def onDownstreamFinish(cause: Throwable): Unit = {
            promise.failure(cause)
            super.onDownstreamFinish(cause)
          }
        })
      }

      (logic, promise.future)
    }
  }

  def main(args: Array[String]): Unit = {
    import system.dispatcher

    val counterFuture: Future[Int] =
      Source(1 to 10)
        // .map(x => if (x == 7) throw new RuntimeException("Gotcha!") else x) - test 'upStreamFailure`
        .viaMat(Flow.fromGraph(new CounterFlow[Int]))(Keep.right)
        .to(Sink.foreach[Int]((x: Int) => if (x == 10) throw new RuntimeException("Gotcha!") else println(x)))
        // .to(Sink.foreach[Int](println))
        .run()

    counterFuture.onComplete {
      case Success(count) => println(s"The number of elements passed: $count")
      case Failure(exception) => println(s"Flow failed due to: $exception")
    }
  }
}
