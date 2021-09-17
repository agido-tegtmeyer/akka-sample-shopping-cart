package shopping.cart.behaviors

import akka.actor.{ActorRef => TActorRef}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import shopping.cart.CborSerializable
import shopping.cart.proto.FactorialResponse

object FactorialBehavior {


  val EntityKey: EntityTypeKey[FactorialCommand] =
    EntityTypeKey[FactorialCommand]("FactorialBehavior")


  sealed trait FactorialCommand extends CborSerializable

  case class ComputeFactorial(number: Int, replyTo: TActorRef) extends FactorialCommand

  final case class Response(answer: String) extends CborSerializable

  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[FactorialCommand] => Behavior[FactorialCommand] = {
      entityContext =>
        FactorialBehavior(entityContext.entityId)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  def apply(workerId: String): Behavior[FactorialCommand] = {
    Behaviors.receiveMessagePartial {
      case ComputeFactorial(number, replyTo) =>

        val result = factorial(number)

        replyTo ! FactorialResponse(result)

        Behaviors.same
    }
  }

  private def factorial(seed: Long): Long =
    seed match {
      case 0 => 1
      case n => n * factorial(n - 1)
    }

}
