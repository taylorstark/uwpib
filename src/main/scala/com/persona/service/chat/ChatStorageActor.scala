package com.persona.service.chat

import java.util.UUID

import akka.actor.Actor
import com.persona.service.chat.dao.ChatDAO

import scala.concurrent.ExecutionContext.Implicits._

sealed trait DBEvent
case class FetchHistory(offerId: UUID, userid: String) extends DBEvent
case class PersistMsg(offerId: UUID, userid: String, msg: ChatMessage) extends DBEvent

class ChatStorageActor(chatDAO: ChatDAO) extends Actor {

  override def receive = {
    case evt: FetchHistory =>
      val roomActor = sender()
      chatDAO.fetchMsgHistory(evt.offerId, evt.userid)
        .onSuccess {
          case msgs => {
            for (msg <- msgs) {
              roomActor ! HistoryMessage(msg._1, ChatMessage(msg._2, msg._3))
            }
          }
        }


    case evt: PersistMsg =>
      chatDAO.storeMsg(evt.offerId, evt.userid, evt.msg)
  }

}
