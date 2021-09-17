package shopping.cart

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, DispatcherSelector}
import akka.actor.{ActorRef => TActorRef}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.pattern.StatusReply
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import akka.util.Timeout
import io.grpc.Status
import org.slf4j.LoggerFactory
import shopping.cart.behaviors.FactorialBehavior.ComputeFactorial
import shopping.cart.behaviors.StreamBehavior.{Compute, Response}
import shopping.cart.behaviors.{FactorialBehavior, ShoppingCart, SimpleResponder, StreamBehavior}
import shopping.cart.proto._
import shopping.cart.repository.{ItemPopularityRepository, ScalikeJdbcSession}

import java.util.concurrent.TimeoutException
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

class ShoppingCartServiceImpl(system: ActorSystem[_],
                              itemPopularityRepository: ItemPopularityRepository)
  extends proto.ShoppingCartService {


  import system.executionContext

  implicit val materializer = system

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  def sha256Hash(text: String): String = String.format("%064x", new java.math.BigInteger(1, java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"))))

  @tailrec
  private def times(iterations: Int, string: String): String = {
    iterations match {
      case x if x > 0 =>
        times(x - 1, sha256Hash(string))
      case _ => string
    }
  }

  override def sha256(in: shopping.cart.proto.Sha256Request):
  scala.concurrent.Future[shopping.cart.proto.Sha256Response] = {
    val message = in.message
    val iterations = in.iterations
    Future.successful(Sha256Response(times(iterations, message)))
  }


  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(
            Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(
          new GrpcServiceException(
            Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
  }


  override def streamedRequests(in: StreamedRequest): Source[StreamedResponse, NotUsed] = {
    val (a: TActorRef, b) = initializeActorSource[Response]("StreamedRequests")

    (0 to in.number) foreach { i =>
      val entityRef = sharding.entityRefFor(StreamBehavior.EntityKey, i.toString)
      entityRef ! Compute(i, a)
    }

    b.map(x => StreamedResponse(x.answer))
  }

  private def initializeActorSource[T](requestDescription: String): (TActorRef, Source[T, NotUsed]) = {
    val source: Source[T, TActorRef] = Source.actorRef[T](
      completionMatcher = PartialFunction.empty,
      failureMatcher = PartialFunction.empty,
      bufferSize = 10000,
      overflowStrategy = OverflowStrategy.fail
    )
    source.toMat(BroadcastHub.sink[T])(Keep.both).run()
  }

  override def factorialRequests(in: FactorialRequest): Source[FactorialResponse, NotUsed] = {
    val (a: TActorRef, b) = initializeActorSource[FactorialResponse]("FactorialRequest")

    (0 to in.number) foreach { i =>
      val entityRef = sharding.entityRefFor(FactorialBehavior.EntityKey, i.toString)
      entityRef ! ComputeFactorial(i, a)
    }

    b
  }

  override def getFibonacci(in: CalculateFibonacciRequest): Future[CalculateFibonacciResponse] = {
    logger.info("getFibonacci {}", in.number)
    val start = System.currentTimeMillis()
    val fibonacci = calculateFibonacci(in.number)

    val duration = System.currentTimeMillis() - start
    logger.info("result: {} calculated in {}ms", fibonacci, duration)
    val response = Future.successful(CalculateFibonacciResponse(duration, fibonacci.toString))
    convertError(response)
  }

  private def calculateFibonacci(n: Int): Long = {
    n match {
      case 1 | 2 => 1
      case _ => calculateFibonacci(n - 1) + calculateFibonacci(n - 2)
    }
  }

  override def singleRequest(in: SimpleRequest): Future[SimpleResponse] = {
    logger.info(s"singleRequest $in")
    val entityRef = sharding.entityRefFor(SimpleResponder.EntityKey, in.name)
    val reply: Future[SimpleResponder.Response] = entityRef.ask(SimpleResponder.Greet(_))

    val response = reply.map((r: SimpleResponder.Response) => SimpleResponse(r.answer))

    convertError(response)
  }


  // OLD STUFF

  private val blockingJdbcExecutor: ExecutionContext =
    system.dispatchers.lookup(
      DispatcherSelector
        .fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher")
    )

  override def addItem(in: proto.AddItemRequest): Future[proto.Cart] = {
    logger.info("addItem {} to cart {}", in.itemId, in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.AddItem(in.itemId, in.quantity, _))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  override def updateItem(in: proto.UpdateItemRequest): Future[proto.Cart] = {
    logger.info("updateItem {} to cart {}", in.itemId, in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)

    def command(replyTo: ActorRef[StatusReply[ShoppingCart.Summary]]) =
      if (in.quantity == 0)
        ShoppingCart.RemoveItem(in.itemId, replyTo)
      else
        ShoppingCart.AdjustItemQuantity(in.itemId, in.quantity, replyTo)

    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(command(_))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }


  override def checkout(in: proto.CheckoutRequest): Future[proto.Cart] = {
    logger.info("checkout {}", in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.Checkout(_))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  override def getCart(in: proto.GetCartRequest): Future[proto.Cart] = {
    logger.info("getCart {}", in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val response =
      entityRef.ask(ShoppingCart.Get).map { cart =>
        if (cart.items.isEmpty)
          throw new GrpcServiceException(
            Status.NOT_FOUND.withDescription(s"Cart ${in.cartId} not found"))
        else
          toProtoCart(cart)
      }
    convertError(response)
  }


  private def toProtoCart(cart: ShoppingCart.Summary): proto.Cart = {
    proto.Cart(
      cart.items.iterator.map { case (itemId, quantity) =>
        proto.Item(itemId, quantity)
      }.toSeq,
      cart.checkedOut)
  }


  override def getItemPopularity(in: proto.GetItemPopularityRequest): Future[proto.GetItemPopularityResponse] = {
    Future {
      ScalikeJdbcSession.withSession { session =>
        itemPopularityRepository.getItem(session, in.itemId)
      }
    }(blockingJdbcExecutor).map {
      case Some(count) =>
        proto.GetItemPopularityResponse(in.itemId, count)
      case None =>
        proto.GetItemPopularityResponse(in.itemId, 0L)
    }
  }
}

