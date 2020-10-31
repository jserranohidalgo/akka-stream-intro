package tcp

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{Broadcast, Flow, Framing, GraphDSL, Interleave, RunnableGraph, Sink, Source, Tcp}
import akka.stream.{ActorAttributes, ClosedShape, Supervision}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

object SeedlinkTcp extends App{
  private implicit val system: ActorSystem = ActorSystem("TCP_Server_Actor_System")
  private implicit val ec: ExecutionContext = system.dispatcher

  val testSupervisionDecider: Supervision.Decider = {
    case ex: java.lang.RuntimeException =>
      println(s"some run time exception ${ex.getMessage}")
      Supervision.Stop
    case ex: Exception =>
      println("Exception occurred and stopping stream",
        ex)
      Supervision.Stop
  }

  val seedlinkC = Tcp().outgoingConnection("127.0.0.1", 8890)

  val g = RunnableGraph.fromGraph(GraphDSL.create(seedlinkC) {
    implicit b => sl =>
      import GraphDSL.Implicits._

      val stations = Source(List("emin","ezar", "espr", "tlor", "ccho"))
        .flatMapConcat{ station => Source(List(s"station $station es\n", "data\n")) }
        .map(Right.apply)

      val interleave = b.add(Interleave[Either[String, String]](2, 1))

      val check = b.add(Flow[Either[String, String]].map{
        case Left(response) if response != "OK" => throw new Exception("error")
        case element => element
      })

      val filter = b.add(Flow[Either[String, String]].filter(_.isRight).map{
        case Right(command) => command
      })

      val bcast = b.add(Broadcast[Either[String, String]](2))

      val fromBStoS = Flow.fromFunction[ByteString, Either[String, String]](bs => Left(bs.utf8String))
      val fromStoBS = Flow.fromFunction[String, ByteString](s => {println(s); ByteString(s)})

      stations ~> interleave.in(0)
      sl.out ~> fromBStoS ~> interleave.in(1)
      interleave.out ~> bcast ~> Sink.foreach{println}
      bcast ~> check ~> filter ~> fromStoBS ~> sl.in
      ClosedShape
  }).withAttributes(ActorAttributes.supervisionStrategy(testSupervisionDecider))

  g.run()/*.onComplete{
    case Failure(exception) => println(exception.getMessage) ; system.terminate()
    case Success(_) => system.terminate() }
  Await.result(system.whenTerminated, Duration.Inf)*/
}

object SeedlinkServer extends App {
  implicit val system = ActorSystem("QuickStart")

  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind("127.0.0.1", 8890)

  connections.runForeach{ connection =>
    import connection._

    val serverLogic = Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)
      .map(_.utf8String)
      .takeWhile(_ != "BYE")
      .map(_ => "OK")
      .map(ByteString(_))

    connection.handleWith(serverLogic)
  }
  // Await.result(system.whenTerminated, Duration.Inf)
}
