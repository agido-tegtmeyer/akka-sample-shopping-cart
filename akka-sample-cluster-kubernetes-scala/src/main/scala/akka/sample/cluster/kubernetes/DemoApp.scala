package akka.sample.cluster.kubernetes

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import akka.{actor => classic}
import shopping.cart.behaviors.{FactorialBehavior, FibonacciBehavior, ShoppingCart, SimpleResponder, StreamBehavior}
import shopping.cart.{PowerBehavior, ShoppingCartServer, ShoppingCartServiceImpl}

object DemoApp extends App {

  ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
    import akka.actor.typed.scaladsl.adapter._
    implicit val classicSystem: classic.ActorSystem = context.system.toClassic
    implicit val ec = context.system.executionContext

    val cluster = Cluster(context.system)
    context.log.info("Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

    Http().newServerAt("0.0.0.0", 8080).bind(complete("Hello world - Return of the Greeting"))

    // Create an actor that handles cluster domain events
    val listener = context.spawn(Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
      ctx.log.info("MemberEvent: {}", event)
      Behaviors.same
    }), "listener")

    Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

    AkkaManagement.get(classicSystem).start()
    ClusterBootstrap.get(classicSystem).start()

    val system = context.system
    ShoppingCart.init(system)

    SimpleResponder.init(system)
    StreamBehavior.init(system)
    PowerBehavior.init(system)
    FactorialBehavior.init(system)
    FibonacciBehavior.init(system)


    val grpcInterface = "0.0.0.0"
    val grpcPort = 8081
    val grpcService = new ShoppingCartServiceImpl(context.system)
    ShoppingCartServer.start(grpcInterface, grpcPort, context.system, grpcService)

    Behaviors.empty
  }, "appka")
}
