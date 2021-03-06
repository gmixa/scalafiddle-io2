/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package scalafiddle.router

import akka.actor._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import org.slf4j.LoggerFactory

/**
  * Provides a flow that is handled by an actor.
  *
  * Copied from Play
  *
  * See https://github.com/akka/akka/issues/16985.
  */
object ActorFlow {
  val log = LoggerFactory.getLogger(getClass)

  /**
    * Create a flow that is handled by an actor.
    *
    * Messages can be sent downstream by sending them to the actor passed into the props function.  This actor meets
    * the contract of the actor returned by [[akka.stream.scaladsl.Source.actorRef]].
    *
    * The props function should return the props for an actor to handle the flow. This actor will be created using the
    * passed in [[akka.actor.ActorRefFactory]]. Each message received will be sent to the actor - there is no back pressure,
    * if the actor is unable to process the messages, they will queue up in the actors mailbox. The upstream can be
    * cancelled by the actor terminating itself.
    *
    * @param props A function that creates the props for actor to handle the flow.
    * @param bufferSize The maximum number of elements to buffer.
    * @param overflowStrategy The strategy for how to handle a buffer overflow.
    */
  def actorRef[In, Out](
      props: ActorRef => Props,
      terminated: () => Unit,
      bufferSize: Int = 16,
      overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew
  )(implicit factory: ActorRefFactory, mat: Materializer): Flow[In, Out, _] = {

    val (outActor, publisher) = Source
      .actorRef[Out](
        {
          case akka.actor.Status.Success(s: CompletionStrategy)                              => s
          case akka.actor.Status.Success(_)                                                  => CompletionStrategy.draining
          case akka.actor.Status.Success                                                     => CompletionStrategy.draining
        }: PartialFunction[Any, CompletionStrategy], { case akka.actor.Status.Failure(cause) => cause }: PartialFunction[
          Any,
          Throwable
        ],
        bufferSize,
        overflowStrategy
      )
      .toMat(Sink.asPublisher(false))(Keep.both)
      .run()

    Flow.fromSinkAndSource(
      Sink.actorRef(
        factory.actorOf(Props(new Actor {
          val flowActor = context.watch(context.actorOf(props(outActor), "flowActor"))

          def receive = {
            case Status.Success(_) | Status.Failure(_) =>
              flowActor ! PoisonPill
            case Terminated(_) =>
              log.debug("Child terminated, stopping")
              context.stop(self)
            case other =>
              flowActor ! other
          }

          override def supervisorStrategy = OneForOneStrategy() {
            case e =>
              log.error("Stopping actor due to exception", e)
              SupervisorStrategy.Stop
          }

          override def postStop(): Unit = {
            terminated()
            super.postStop()
          }
        })),
        Status.Success(()),
        ex => Status.Failure(ex)
      ),
      Source.fromPublisher(publisher)
    )
  }
}
