package com.akkagame.actor

import akka.actor.{Actor, ActorRef}

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
