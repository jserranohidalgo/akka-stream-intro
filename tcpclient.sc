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
import scala.io.StdIn, StdIn._
import java.nio.file.Paths
import akka.stream.scaladsl._, Tcp._


implicit val system = ActorSystem("QuickStart")

val connection = Tcp().outgoingConnection("127.0.0.1", 8890)

val replParser =
  Flow[String].takeWhile(_ != "q").concat(Source.single("BYE")).map(elem => ByteString(s"$elem\n"))

val repl = Flow[ByteString]
  .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
  .map(_.utf8String)
  .map(text => println("Server: " + text))
  .map(_ => readLine("> "))
  .via(replParser)

val connected = connection.join(repl).run()