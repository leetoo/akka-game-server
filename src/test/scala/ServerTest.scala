import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import com.akkagame.service.GameService
import org.scalatest.{FunSuite, Matchers}

class ServerTest extends FunSuite with Matchers with ScalatestRouteTest {
  /**
    * use ~testQuick in sbt terminal for continuous testing
    */
  test("should respond with correct message ") {
    assertWebsocket("John") { wsClient =>
      // check response for WS Upgrade headers
      wsClient.expectMessage("[{\"name\":\"John\",\"position\":{\"x\":0,\"y\":0}}]")
      /*wsClient.sendMessage(TextMessage("hello"))
      wsClient.expectMessage("hello")*/
    }
  }
  test("should register player") {
    assertWebsocket("John") { wsClient =>
      wsClient.expectMessage("[{\"name\":\"John\",\"position\":{\"x\":0,\"y\":0}}]")
    }
  }
  test("should register player and move it up ") {
    assertWebsocket("John") { wsClient =>
      wsClient.expectMessage("[{\"name\":\"John\",\"position\":{\"x\":0,\"y\":0}}]")
      wsClient.sendMessage("up")
      wsClient.expectMessage("[{\"name\":\"John\",\"position\":{\"x\":0,\"y\":1}}]")
    }
  }




  test("should register multiple players") {
    val gameService = new GameService()
    val johnClient = WSProbe()
    val andrewClient = WSProbe()
    WS(s"/?playerName=John", johnClient.flow) ~> gameService.websocketRoute ~> check {
      johnClient.expectMessage("[{\"name\":\"John\",\"position\":{\"x\":0,\"y\":0}}]")
    }
    WS(s"/?playerName=Andrew", andrewClient.flow) ~> gameService.websocketRoute ~> check {
      andrewClient.expectMessage("[{\"name\":\"John\",\"position\":{\"x\":0,\"y\":0}},{\"name\":\"Andrew\",\"position\":{\"x\":0,\"y\":0}}]")
    }

  }
  def assertWebsocket(playerName: String)(assertions: WSProbe => Unit): Unit = {
    val gameService = new GameService()
    val wsClient = WSProbe()
    // WS creates a WebSocket request for testing
    WS(s"/?playerName=$playerName", wsClient.flow) ~> gameService.websocketRoute ~> check(
      assertions(wsClient))
  }
}



