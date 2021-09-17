package shopping.cart.behaviors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import shopping.cart.CborSerializable

object SimpleResponder {

  val EntityKey: EntityTypeKey[ResponderCommand] =
    EntityTypeKey[ResponderCommand]("Responder")

  sealed trait ResponderCommand extends CborSerializable

  case class Greet(replyTo: ActorRef[Response]) extends ResponderCommand

  final case class Response(answer: String) extends CborSerializable

  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[ResponderCommand] => Behavior[ResponderCommand] = {
      entityContext =>
        SimpleResponder(entityContext.entityId)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }


  def apply(responderId: String): Behavior[ResponderCommand] = {
    Behaviors.receiveMessagePartial {
      case Greet(replyTo) =>
        replyTo ! Response(s"Response from $responderId")
        Behaviors.same
    }
  }

}
