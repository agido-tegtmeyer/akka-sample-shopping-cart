package shopping.cart


import akka.actor.{ActorRef => TActorRef}
import akka.actor.typed.{ ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import shopping.cart.proto.StreamedResponse


object StreamBehavior {


  val EntityKey: EntityTypeKey[StreamCommand] =
    EntityTypeKey[StreamCommand]("StreamBehavior")


  /**
   * This interface defines all the commands (messages) that the ShoppingCart actor supports.
   */
  sealed trait StreamCommand extends CborSerializable

  case class Compute(number: Int, replyTo: TActorRef) extends StreamCommand

  /**
   * Summary of the shopping cart state, used in reply messages.
   */
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
