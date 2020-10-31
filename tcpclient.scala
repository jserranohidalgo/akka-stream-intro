import java.net.InetSocketAddress

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.util.ByteString

import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn
import StdIn._
import java.nio.file.Paths

import akka.stream.scaladsl._
import Tcp._
import akka.stream.ClosedShape

import scala.util.{Failure, Success}

object TcpClient extends App {
  implicit val system = ActorSystem("QuickStart")

  val connection: Flow[ByteString, ByteString, Future[OutgoingConnection]] =
    Tcp().outgoingConnection(InetSocketAddress.createUnresolved("127.0.0.1", 8890), halfClose = false)
    // Tcp().outgoingConnection("127.0.0.1", 8890)

  val toStringF =
    Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)
      .map(_.utf8String)

  val repl: Flow[ByteString, ByteString, _] = toStringF
    .map(text => println("Server: " + text))
    .map(_ => readLine("> "))
    .takeWhile(_ != "q")
    .concat(Source.single("BYE"))
    .map(elem => ByteString(s"$elem\n"))

  val connected = connection.joinMat(repl)((f, _) => f).run()

  RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val source = Source(List(1,2,3,4,5))
    val zip = b.add(ZipWith((left: Int, right: Int) => left))
    val bcast = b.add(Broadcast[Int](2))
    val concat = b.add(Concat[Int]())
    val start = Source.single(0)

    source ~> zip.in0
    zip.out/*.map { s => println(s); s }*/ ~> bcast ~> Sink.foreach{println}
    zip.in1 <~ concat <~ start
    concat         <~          bcast
    ClosedShape
  })
  connected.onComplete{
    case Success(c) => println(c)
    case Failure(exception) => println(exception)
  }(system.dispatcher)
}