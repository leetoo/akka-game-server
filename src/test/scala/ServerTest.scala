import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.scalatest.{FunSuite, Matchers}

class ServerTest extends FunSuite with Matchers with ScalatestRouteTest {
  /**
    * use ~testQuick in sbt terminal for continuous testing
    */
  test("empty") {
    1 shouldBe 1
  }

  test("should create empty GameService") {
    val gameService = new GameService()

    val wsClient = WSProbe()

    // WS creates a WebSocket request for testing
    WS("/greeter", wsClient.flow) ~> gameService.websocketRoute ~>
      check {
        // check response for WS Upgrade headers
        isWebSocketUpgrade shouldEqual true
      }
  }

}

class GameService() extends Directives {
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()
  val websocketRoute = get {
    handleWebSocketMessages(greeter)
  }
  def greeter: Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage =>
        TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }
}
