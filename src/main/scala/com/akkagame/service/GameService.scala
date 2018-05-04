package com.akkagame.service

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import com.akkagame.actor._

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
