
import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent._
import scala.concurrent.duration._
import java.nio.file.Paths
import akka.stream.scaladsl._, Tcp._

object TcpServer extends App {
  implicit val system = ActorSystem("QuickStart")

  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind("127.0.0.1", 8890)

  connections.runForeach{ connection =>
      import connection._
      val welcomeMsg = s"Welcome to: $localAddress, you are: $remoteAddress!"

      val serverLogic = Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)
        .map(_.utf8String)
        .takeWhile(_ != "BYE")
        .map(_ + "!")
        .merge(Source.single(welcomeMsg))
        .map(_ + "\n")
        .map(ByteString(_))

      connection.handleWith(serverLogic)
    }
  // Await.result(system.whenTerminated, Duration.Inf)
}