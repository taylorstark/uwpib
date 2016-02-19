package com.persona.service.chat

import java.util.UUID

import akka.actor.{Props, ActorRef, Actor}
import com.persona.service.chat.dao.ChatDAO

class ChatRoomActor(offerId: UUID, chatDAO: ChatDAO) extends Actor {

  var participants = Map.empty[String, ActorRef]
  var supports = Map.empty[String, ActorRef]

  val dbWorker = context.actorOf(Props(classOf[ChatStorageActor], chatDAO))

  override def receive = {
    case Connect(user, ref) =>
      userType(user) match {
        case UserType.Consumer =>
          participants += user -> ref
          supports.values.foreach(_ ! ChatMessage(user, "Joined"))
          dbWorker ! FetchHistory(offerId, user)
          dbWorker ! PersistMsg(offerId, user, ChatMessage(user, "Joined"))

        case UserType.Partner =>
          supports += user -> ref
      }

    case Disconnect(user) =>
      userType(user) match {
        case UserType.Consumer =>
          participants -= user
        case UserType.Partner =>
          supports -= user
      }

    case ack: AckMessage =>
      dbWorker ! PersistMsg(offerId, ack.user, ChatMessage(ack.user, "Seen"))
      supports.values.foreach(_ ! ChatMessage(ack.user, "Seen"));

    case msg: ChatMessage =>
      if(userType(msg.user) == UserType.Partner) {
          participants.values.foreach(_ ! msg)
      }
      supports.values.foreach(_ ! msg)
      dbWorker ! PersistMsg(offerId, msg.user, msg)

    case msg: HistoryMessage =>
      participants.get(msg.user).get ! msg.msg
  }

  /*
   * TODO: Hook into account services when done
   */
  def userType(user: String) = {
    user match {
      case "support" => UserType.Partner
      case _ => UserType.Consumer
    }
  }

}
