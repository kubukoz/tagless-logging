package matwojcik.tagless.logging
import java.util.concurrent.ForkJoinPool

import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.syntax.apply._
import com.typesafe.scalalogging.StrictLogging
import org.slf4j.MDC

import scala.concurrent.{ExecutionContext, Future}

class MDCAwareEC(underlying: ExecutionContext) extends ExecutionContext {
  override def execute(runnable: Runnable): Unit     = underlying.execute(new RunnableContextAware(runnable))
  override def reportFailure(cause: Throwable): Unit = underlying.reportFailure(cause)
}

class RunnableContextAware(underlying: Runnable) extends Runnable {
  private val storedContext = Option(MDC.getCopyOfContextMap)

  override def run(): Unit = {
    storedContext.foreach(MDC.setContextMap)
    try {
      underlying.run()
    }
    finally {
      MDC.clear()
    }
  }
}

object App extends IOApp.WithContext with StrictLogging {

  //override IOApp's default EC to add keeping MDC context
  override protected def executionContextResource
    : Resource[SyncIO, ExecutionContext] =
    Resource.liftF(SyncIO.pure(new MDCAwareEC(ExecutionContext.global)))

  implicit val secondEc: ExecutionContext = new MDCAwareEC(ExecutionContext.fromExecutor(new ForkJoinPool(5)))

  override def run(args: List[String]): IO[ExitCode] =
    Program.program[IO]("foo1").as(ExitCode.Success) <* IO(logger.info("End log"))

  //parallel execution test:
//    (Program.program[IO]("foo1"), Program.program[IO]("foo2")).parTupled.void.as(ExitCode.Success) <* IO(logger.info("End log"))
}

object Impure extends StrictLogging {

  def function(): Unit =
    logger.debug("Impure logging")

  def futureFunction(implicit ec: ExecutionContext) = Future { logger.debug("Logging in future") }
}

object Program {

  def program[F[_]: Async: Logging: Tracing: DeferFuture](name: String)(implicit CS: ContextShift[F], ec: ExecutionContext): F[Unit] =
    for {
      _      <- Tracing[F].startNewTrace(name)
      _      <- Sync[F].delay(Impure.function())
      v      <- Async[F].pure("value")
      _      <- Logging[F].info(s"log1: $v")
      _      <- Async.shift(ec)
      _      <- Sync[F].delay(Impure.function())
      _      <- DeferFuture[F].defer(Impure.futureFunction)
      _      <- Logging[F].info("log2")

      _      <- Sync[F].delay(println("some println"))

      _      <- Tracing[F].startNewTrace(name + "v2")
      _      <- Logging[F].debug("log3")
    } yield ()

}
