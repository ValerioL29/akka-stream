package part3graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{BidiShape, ClosedShape, FlowShape, Materializer, SinkShape, SourceShape}

object BidirectionalFlows extends App {

  implicit val system: ActorSystem = ActorSystem("BidirectionalFlows")
  implicit val mat: Materializer = Materializer(system)

  /**
   * Example: cryptography
   */
  def encrypt(n: Int)(str: String): String =
    str.map((c: Char) => (c + n).toChar)
  def decrypt(n: Int)(str: String): String =
    str.map((c: Char) => (c - n).toChar)

  // bidi Flow
  val bidiCryptoStaticGraph = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    val encryptionFlowShape: FlowShape[String, String] = builder.add(Flow[String].map(encrypt(3)))
    val decryptionFlowShape: FlowShape[String, String] = builder.add(Flow[String].map(decrypt(3)))

    //    BidiShape(
    //      encryptionFlowShape.in, encryptionFlowShape.out,
    //      decryptionFlowShape.in, decryptionFlowShape.out
    //    )
    // or we can say
    BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
  }

  val unencryptedStrings = List(
    "akka",
    "is",
    "awesome",
    "testing",
    "bidirectional",
    "flows"
  )
  val unencryptedSource = Source(unencryptedStrings)
  val encryptedSource = Source(unencryptedStrings.map(encrypt(3)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val unencryptedSourceShape: SourceShape[String] =
        builder.add(unencryptedSource)
      val encryptedSourceShape: SourceShape[String] =
        builder.add(encryptedSource)
      val bidi: BidiShape[String, String, String, String] =
        builder.add(bidiCryptoStaticGraph)
      val encryptedSinkShape: SinkShape[String] =
        builder.add(Sink.foreach[String]((str: String) => println(s"Encrypted: $str")))
      val decryptedSinkShape: SinkShape[String] =
        builder.add(Sink.foreach[String]((str: String) => println(s"Decrypted: $str")))

      unencryptedSourceShape ~> bidi.in1  ;  bidi.out1 ~> encryptedSinkShape
      decryptedSinkShape     <~ bidi.out2 ;  bidi.in2  <~ encryptedSourceShape

      ClosedShape
    }
  )

  cryptoBidiGraph.run()

  /**
   * - encrypting  / decrypting
   * - encoding    / decoding
   * - serializing / deserializing
   */
  Thread.sleep(1000)
  system.terminate()
}
