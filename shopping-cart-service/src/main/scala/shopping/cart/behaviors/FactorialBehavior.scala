package shopping.cart.behaviors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.{ActorRef => TActorRef}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import org.slf4j.LoggerFactory
import shopping.cart.CborSerializable

object FactorialBehavior {

  private val logger = LoggerFactory.getLogger(getClass)

  val EntityKey: EntityTypeKey[FactorialCommand] =
    EntityTypeKey[FactorialCommand]("FactorialBehavior")


  sealed trait FactorialCommand extends CborSerializable

  case class ComputeFactorial(number: Int, replyTo: TActorRef) extends FactorialCommand

  final case class Response(seed: Long, factorial: Long) extends CborSerializable

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
        logger.info(s"Calculating factorial for: $number")

        val factorialResult: Long = factorial(number)
        logger.info(s"Factorial for: $number is: $factorialResult")
        replyTo ! Response(number, factorialResult)

        Behaviors.same
    }
  }

  private def factorial(seed: Long): Long =
    seed match {
      case 0 => 1
      case n => n * factorial(n - 1)
    }

}
