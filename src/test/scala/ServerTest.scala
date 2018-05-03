import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge}
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
        wsClient.expectMessage("welcome player")
        wsClient.sendMessage(TextMessage("hello"))
        wsClient.expectMessage("hello")
      }
  }
  test("should register player") {
    val gameService = new GameService()
    val wsClient = WSProbe()

    // WS creates a WebSocket request for testing
    WS("/", wsClient.flow) ~> gameService.websocketRoute ~>
      check {
        // check response for WS Upgrade headers
        wsClient.expectMessage("welcome player")
      }
  }
}
class GameService() extends Directives {
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  val websocketRoute: Route = get {
    handleWebSocketMessages(greeter)
  }
  def greeter: Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    //    val materialization = builder.materializedValue.map(m => TextMessage("welcome player"))
    val materialization = builder.materializedValue.map(_ => TextMessage("welcome player"))
    val massagePassingFlow = builder.add(Flow[Message].map(m => m))
    val merge = builder.add(Merge[Message](2))
    materialization ~> merge.in(0)
    merge ~> massagePassingFlow
    FlowShape(merge.in(1), massagePassingFlow.out)
  })
}
