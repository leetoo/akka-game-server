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
  test("should respond with correct message ") {
    val gameService = new GameService()
    val wsClient = WSProbe()

    // WS creates a WebSocket request for testing
    WS("/?playerName=John", wsClient.flow) ~> gameService.websocketRoute ~>
      check {
        // check response for WS Upgrade headers
        wsClient.expectMessage("welcome John")
        wsClient.sendMessage(TextMessage("hello"))
        wsClient.expectMessage("hello")
      }
  }
  test("should register player") {
    val gameService = new GameService()
    val wsClient = WSProbe()

    // WS creates a WebSocket request for testing
    WS("/?playerName=John", wsClient.flow) ~> gameService.websocketRoute ~>
      check {
        // check response for WS Upgrade headers
        wsClient.expectMessage("welcome John")
      }
  }
}
class GameService() extends Directives {
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  val websocketRoute: Route = (get & parameter("playerName")){ playerName =>
    handleWebSocketMessages(flow(playerName))
  }
  def flow(playerName:String ) : Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create() {
    implicit builder =>
    import GraphDSL.Implicits._
    //    val materialization = builder.materializedValue.map(m => TextMessage("welcome player"))
    val materialization = builder.materializedValue.map(_ => TextMessage(s"welcome $playerName"))
    val massagePassingFlow = builder.add(Flow[Message].map(m => m))
    val merge = builder.add(Merge[Message](2))
    materialization ~> merge.in(0)
    merge ~> massagePassingFlow
    FlowShape(merge.in(1), massagePassingFlow.out)
  })
}
