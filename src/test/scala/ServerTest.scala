import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import org.scalatest.{FunSuite, Matchers}

class ServerTest extends FunSuite with Matchers with ScalatestRouteTest {
  /**
    * use ~testQuick in sbt terminal for continuous testing
    */
 /* test("should respond with correct message ") {
    assertWebsocket("John") { wsClient =>
      // check response for WS Upgrade headers
      wsClient.expectMessage("[{\"name\":\"John\"}]")
      wsClient.sendMessage(TextMessage("hello"))
      wsClient.expectMessage("hello")
    }
  }
  test("should register player") {
    assertWebsocket("John") { wsClient =>
      wsClient.expectMessage("[{\"name\":\"John\"}]")
    }
  }*/
  test("should register player and move it up ") {
    assertWebsocket("John") { wsClient =>
      wsClient.expectMessage(("[{\"name\":\"John\",\"position\":{\"x\":0,\"y\":0}}]"))
    }
  }

  // todo need to repair the tests
  /*test("should register multiple players") {
    val gameService = new GameService()
    val johnClient = WSProbe()
    val andrewClient = WSProbe()
    WS(s"/?playerName=john", johnClient.flow) ~> gameService.websocketRoute ~> check {
      johnClient.expectMessage("[{\"name\":\"john\"}]")
    }
    WS(s"/?playerName=andrew", andrewClient.flow) ~> gameService.websocketRoute ~> check {
      andrewClient.expectMessage("[{\"name\":\"john\"},{\"name\":\"andrew\"}]")
    }
  }*/
  def assertWebsocket(playerName: String)(assertions: WSProbe => Unit): Unit = {
    val gameService = new GameService()
    val wsClient = WSProbe()
    // WS creates a WebSocket request for testing
    WS(s"/?playerName=$playerName", wsClient.flow) ~> gameService.websocketRoute ~> check(
      assertions(wsClient))
  }
}
class GameService() extends Directives {
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  val websocketRoute: Route = (get & parameter("playerName")) { playerName =>
    handleWebSocketMessages(flow(playerName))
  }
  val gameAreaActor = actorSystem.actorOf(Props(new GameAreaActor()))
  val playerActorSource = Source.actorRef[GameEvent](5, OverflowStrategy.fail)
  def flow(playerName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(playerActorSource) {
    implicit builder =>
      playerActor =>
        import GraphDSL.Implicits._
        // val playerActor
        //    val materialization = builder.materializedValue.map(m => TextMessage("welcome player"))
        val materialization = builder.materializedValue.map(playerActorRef => PlayerJoined(Player
        (playerName,Position(0,0) ),
          playerActorRef))
        // val massagePassingFlow = builder.add(Flow[Message].map(m => m))
        val merge = builder.add(Merge[GameEvent](2))
        // we need to convert raw ws messages to our domain messages
        val messagesToGameEventsFlow = builder.add(Flow[Message].map {
          case TextMessage.Strict(direction) => PlayerMoveRequest(playerName, direction )
        })
        val gameEventsToMessagesFlow = builder.add(Flow[GameEvent].map {
          case PlayersChanged(players) => {
            import spray.json._
            import DefaultJsonProtocol._
            implicit val positionFormat = jsonFormat2(Position)
            implicit val playerFormat = jsonFormat2(Player)
            TextMessage(players.toJson.toString)
          }
          case PlayerMoveRequest(player, direction) => TextMessage(direction)
        })
        val gameAreaActorSink = Sink.actorRef[GameEvent](gameAreaActor, PlayerLeft(playerName))
        materialization ~> merge ~> gameAreaActorSink
        messagesToGameEventsFlow ~> merge
        playerActor ~> gameEventsToMessagesFlow
        FlowShape(messagesToGameEventsFlow.in, gameEventsToMessagesFlow.out)
  })
}
class GameAreaActor extends Actor {
  val players = collection.mutable.LinkedHashMap[String, PlayerWithActor]()
  override def receive: Receive = {
    case PlayerJoined(player, actor) => {
      players += (player.name -> PlayerWithActor(player, actor))
      notifyPlayersChanged()
    }
    case PlayerLeft(playerName) => {
      players -= playerName
      notifyPlayersChanged()
    }
    case PlayerMoveRequest(playerName, direction) => {
      val offset = direction match {
        case "up" => Position(0, 1)
        case "down" => Position(0, -1)
        case "left" => Position(1, 0)
        case "right" => Position(-1, 0)
      }
      val oldPlayerWithActor = players(playerName)
      val oldPlayer = oldPlayerWithActor.player
      val actor = oldPlayerWithActor.actor
      players(playerName) = PlayerWithActor(Player(playerName,oldPlayer.position + offset),actor)
      notifyPlayersChanged()
    }
  }
  def notifyPlayerMoveRequested(playerMoveRequest: PlayerMoveRequest) = {
    players.values.foreach(_.actor ! playerMoveRequest)
  }
  def notifyPlayersChanged(): Unit = {
    players.values.foreach(_.actor ! PlayersChanged(players.values.map(_.player)))
  }
}
trait GameEvent
case class Player(name: String, position: Position)
case class PlayerMoveRequest(playerName: String, direction: String) extends GameEvent
case class PlayerLeft(playerName: String) extends GameEvent
case class PlayerJoined(player: Player, actorRef: ActorRef) extends GameEvent
case class PlayerWithActor(player: Player, actor: ActorRef)
case class PlayersChanged(players: Iterable[Player]) extends GameEvent
case class Position(x: Int, y: Int) {
  def + (other:Position ) :Position = {
    Position (x+other.x, y+other.y)
  }
}
