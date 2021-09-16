package shopping.cart

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}


object DavidBehavior {


  val EntityKey: EntityTypeKey[DavidCommand] =
    EntityTypeKey[DavidCommand]("DavidBehavior")



  /**
   * This interface defines all the commands (messages) that the ShoppingCart actor supports.
   */
  sealed trait DavidCommand extends CborSerializable

  case class Compute(replyTo: ActorRef[Response]) extends DavidCommand

  /**
   * Summary of the shopping cart state, used in reply messages.
   */
  final case class Response(answer: String) extends CborSerializable


  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[DavidCommand] => Behavior[DavidCommand] = {
      entityContext =>
        DavidBehavior(entityContext.entityId)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }


  def apply(davidId: String): Behavior[DavidCommand] = {
    Behaviors.receiveMessagePartial {
      case Compute(replyTo) =>
        replyTo ! Response(s"some reply for Compute request to $davidId")
        Behaviors.same
    }
  }

}
