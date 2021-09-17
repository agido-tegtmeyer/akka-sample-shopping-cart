package shopping.cart.behaviors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.{ActorRef => TActorRef}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import org.slf4j.LoggerFactory
import shopping.cart.CborSerializable

object StreamBehavior {

  private val logger = LoggerFactory.getLogger(getClass)


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
        logger.info(s"Hi there, I am working on number $number")

        Thread.sleep(number * 10)

        replyTo ! Response(s"Did some work for ${number * 10} ms")

        Behaviors.same
    }
  }

}
