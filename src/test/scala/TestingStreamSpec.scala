import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.pattern.pipe
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class TestingStreamSpec extends TestKit(ActorSystem("TestingAkkaStreams"))
  with AnyWordSpecLike
  with BeforeAndAfterAll {

  implicit val mat: Materializer = Materializer(system)
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A simple stream" should {
    val simpleSource: Source[Int, NotUsed] = Source(1 to 10)
    val simpleSink: Sink[Int, Future[Int]] = Sink.fold(0)((a: Int, b: Int) => a + b)

    "satisfy basic assertions" in {
      // describe our test

      val sumFuture: Future[Int] = simpleSource.toMat(simpleSink)(Keep.right).run()
      val sum: Int = Await.result(sumFuture, 2 seconds)
      assert(sum == 55)
    }

    "integrate with test actors via materialized values" in {
      import system.dispatcher

      val probe: TestProbe = TestProbe()

      simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref)

      probe.expectMsg(55)
    }

    "integrate with a test-actor-based sink" in {
      val anotherSimpleSource: Source[Int, NotUsed] = Source(1 to 5)
      val flow: Flow[Int, Int, NotUsed] = Flow[Int].scan[Int](0)((_: Int) + (_: Int)) // 0, 1, 3, 6,1 10, 15
      val streamUnderTest: Source[Int, NotUsed] = anotherSimpleSource.via(flow)

      val probe: TestProbe = TestProbe()
      val probeSink: Sink[Any, NotUsed] =
        Sink.actorRef(probe.ref, "completionMessage")

      streamUnderTest.to(probeSink).run()
      probe.expectMsgAllOf(0, 1, 3, 6, 10, 15)
    }

    "integrate with Streams TestKit Sink" in {
      val sourceUnderTest: Source[Int, NotUsed] = Source(1 to 5).map((_: Int) * 2)

      val testSink: Sink[Int, TestSubscriber.Probe[Int]] = TestSink.probe[Int]
      val materializedTestValue: TestSubscriber.Probe[Int] = sourceUnderTest.runWith(testSink)

      materializedTestValue
        .request(5) // send a signal to the TestSink
        .expectNext(2, 4, 6, 8, 10)
        .expectComplete()
    }

    "integrate with Streams TestKit Source" in {
      import system.dispatcher
      val sinkUnderTest: Sink[Int, Future[Done]] = Sink.foreach[Int] {
        case 13 => throw new RuntimeException("bad luck!")
        case _ =>
      }

      val testSource: Source[Int, TestPublisher.Probe[Int]] = TestSource.probe[Int]
      val materialized: (TestPublisher.Probe[Int], Future[Done]) =
        testSource.toMat(sinkUnderTest)(Keep.both).run()
      val (testPublisher, resultFuture) = materialized

      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(13)
        .sendComplete()

      resultFuture.onComplete {
        case Success(_) => fail("the sink under test should have thrown an exception on 13")
        case Failure(_) =>
      }
    }

    "test flows wih a test source AND a test sink" in {
      val flowUnderTest: Flow[Int, Int, NotUsed] = Flow[Int].map((_: Int) * 2)

      val testSource: Source[Int, TestPublisher.Probe[Int]] = TestSource.probe[Int]
      val testSink: Sink[Int, TestSubscriber.Probe[Int]] = TestSink.probe[Int]

      val materializedValue: (TestPublisher.Probe[Int], TestSubscriber.Probe[Int]) =
        testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
      val (publisher, subscriber) = materializedValue

      publisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(42)
        .sendNext(99)
        .sendComplete()
      subscriber
        .request(4)
        .expectNext(2, 10, 84, 198)
        .expectComplete()
    }
  }
}
