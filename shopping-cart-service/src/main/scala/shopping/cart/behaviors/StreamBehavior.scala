package shopping.cart.behaviors

import akka.actor.{ActorRef => TActorRef}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import shopping.cart.CborSerializable
import shopping.cart.proto.StreamedResponse

object StreamBehavior {


  val EntityKey: EntityTypeKey[StreamCommand] =
    EntityTypeKey[StreamCommand]("StreamBehavior")


  sealed trait StreamCommand extends CborSerializable

  case class Compute(number: Int, replyTo: TActorRef) extends StreamCommand

  final case class Response(answer: String) extends CborSerializable

  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[StreamCommand] => Behavior[StreamCommand] = {
      entityContext =>
        StreamBehavior(entityContext.entityId)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  def apply(workerId: String): Behavior[StreamCommand] = {
    Behaviors.receiveMessagePartial {
      case Compute(number, replyTo) =>

        Thread.sleep(number)

        replyTo ! StreamedResponse(s"Did some work for $number ms")

        Behaviors.same
    }
  }

}
