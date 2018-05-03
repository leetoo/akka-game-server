import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
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
    WS("/", wsClient.flow) ~> gameService.websocketRoute ~>
      check {
        // check response for WS Upgrade headers
        //  isWebSocketUpgrade shouldEqual true
        wsClient.sendMessage(TextMessage("hello"))
        wsClient.expectMessage("hello")
      }
  }
}
class GameService() extends Directives {
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  val websocketRoute: Route = get {
    handleWebSocketMessages(greeter)
  }
  def greeter: Flow[Message, Message, Any] =
    Flow[Message].collect {
      case TextMessage.Strict(txt) => TextMessage(txt)
    }
}
