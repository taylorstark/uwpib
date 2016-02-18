package com.persona.service.chat

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import com.persona.service.chat.dao.ChatDAO

class ChatService(dataAccess: ChatDAO)(implicit actorSystem: ActorSystem, materializer: Materializer) {

  implicit val chatDAO = dataAccess

  def chat(offerId: UUID, user: String) = {
    ChatRooms.find(offerId) match {
      case Some(room) => handleWebsocketMessages(room.websocketFlow(user))
      case None => complete(StatusCodes.NotFound)
    }
  }

  def createRoom(offerId: UUID): Unit = {
    ChatRooms.createRoom(offerId)
  }

}