import $ivy.`com.typesafe.akka::akka-http:10.2.1`
import $ivy.`com.typesafe.akka::akka-http-spray-json:10.2.1`
import $ivy.`com.typesafe.akka::akka-actor-typed:2.6.10`
import $ivy.`com.typesafe.akka::akka-stream:2.6.10`
import $ivy.`ch.qos.logback:logback-classic:1.2.3`

import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent._
import scala.concurrent.duration._
import java.nio.file.Paths
import akka.stream.scaladsl._, Tcp._

implicit val system = ActorSystem("QuickStart")
val connections: Source[IncomingConnection, Future[ServerBinding]] =
  Tcp().bind("127.0.0.1", 8890)
connections
  .to(Sink.foreach { connection =>
    // server logic, parses incoming commands
    val commandParser = Flow[String].takeWhile(_ != "BYE").map(_ + "!")

    import connection._
    val welcomeMsg = s"Welcome to: $localAddress, you are: $remoteAddress!"
    val welcome = Source.single(welcomeMsg)

    val serverLogic = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
      .map(_.utf8String)
      .via(commandParser)
      // merge in the initial banner after parser
      .merge(welcome)
      .map(_ + "\n")
      .map(ByteString(_))

    connection.handleWith(serverLogic)
  })
  .run()
 Await.result(system.whenTerminated, Duration.Inf)
