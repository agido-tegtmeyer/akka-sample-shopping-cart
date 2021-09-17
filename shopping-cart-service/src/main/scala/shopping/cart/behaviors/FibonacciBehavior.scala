package shopping.cart.behaviors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.{ActorRef => TActorRef}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import org.slf4j.LoggerFactory
import shopping.cart.CborSerializable

object FibonacciBehavior {

  val EntityKey: EntityTypeKey[StreamCommand] =
    EntityTypeKey[StreamCommand]("FibonacciBehavior")

  private val logger = LoggerFactory.getLogger(getClass)

  sealed trait StreamCommand extends CborSerializable

  case class Compute(number: Int, replyTo: TActorRef) extends StreamCommand

  final case class Response(duration: Long, number: Int, result: String) extends CborSerializable

  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[StreamCommand] => Behavior[StreamCommand] = {
      entityContext =>
        FibonacciBehavior(entityContext.entityId)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  def apply(workerId: String): Behavior[StreamCommand] = {
    Behaviors.receiveMessagePartial {
      case Compute(number, replyTo) =>

        val start = System.currentTimeMillis()
        val fibonacci = calculateFibonacci(number)
        val duration = System.currentTimeMillis() - start
        logger.info(s"fibonacci $number result: $fibonacci calculated in ${duration}ms")

        replyTo ! Response(duration,  number, fibonacci.toString)

        Behaviors.same
    }
  }

  private def calculateFibonacci(n: Int): Long = {
    n match {
      case 0 => 0
      case 1 | 2 => 1
      case _ => calculateFibonacci(n - 1) + calculateFibonacci(n - 2)
    }
  }
}
