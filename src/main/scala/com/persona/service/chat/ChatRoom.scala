package com.persona.service.chat

import java.util.UUID

import akka.actor.{Props, ActorSystem}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.{FlowShape, OverflowStrategy}
import akka.stream.scaladsl._
import com.persona.service.chat.dao.ChatDAO

class ChatRoom(id: UUID, actorSystem: ActorSystem, chatDAO: ChatDAO) {

  private[this] val chatActor = actorSystem.actorOf(Props(classOf[ChatRoomActor], id, chatDAO))

  /*
   * Change webflow to parse JSON
   */
  def websocketFlow(user: String) = {
    Flow.fromGraph(
      GraphDSL.create(Source.actorRef[ChatMessage](bufferSize = 100, OverflowStrategy.dropHead)) {
        implicit builder => {
          chatSource => {
            import GraphDSL.Implicits._

            val fromSocket = builder.add(
              Flow[Message].collect {
                case TextMessage.Strict(txt) => ChatMessage(user, txt)
              }
            )

            val toSocket = builder.add(
              Flow[ChatMessage].map {
                case ChatMessage(sender, txt) => TextMessage(s"[$sender]: $txt")
              }
            )

            val merge = builder.add(Merge[ChatEvent](2))

            val chatActorSink = Sink.actorRef[ChatEvent](chatActor, Disconnect(user))

            val actorSource = builder.materializedValue.map(ref => Connect(user, ref))

            fromSocket ~> merge.in(0)
            actorSource ~> merge.in(1)

            merge ~> chatActorSink
            chatSource ~> toSocket

            FlowShape(fromSocket.in, toSocket.out)
          }
        }
      }
    )
  }

}

object ChatRooms {

  var chatRooms = Map.empty[UUID, ChatRoom]

  def find(id: UUID)(implicit actorSystem: ActorSystem): Option[ChatRoom] = chatRooms.get(id)

  def createRoom(id: UUID)(implicit actorSystem: ActorSystem, chatDAO: ChatDAO): Unit = {
    val room = new ChatRoom(id, actorSystem, chatDAO)
    chatRooms += id -> room
  }

}