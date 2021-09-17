package shopping.cart

import akka.actor.{ActorRef => TActorRef}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}


object PowerBehavior {


  val EntityKey: EntityTypeKey[PowerCommand] =
    EntityTypeKey[PowerCommand]("PowerBehavior")

  sealed trait PowerCommand extends CborSerializable

  case class ComputePower(x: Double, exponent: Long, replyTo: TActorRef) extends PowerCommand

  final case class Response(x: Double, exponent: Long, result: Double) extends CborSerializable

  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[PowerCommand] => Behavior[PowerCommand] = {
      entityContext =>
        PowerBehavior(entityContext.entityId)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  def apply(actorId: String): Behavior[PowerCommand] = {
    Behaviors.receiveMessagePartial {
      case ComputePower(x, exponent, replyTo) =>
        replyTo ! Response(x, exponent, scala.math.pow(x,exponent.toDouble))
        Behaviors.same
    }
  }
}