package matwojcik.tagless.logging
import java.util.UUID

import cats.effect.Sync
import cats.tagless.finalAlg
import org.slf4j.MDC

@finalAlg
trait Tracing[F[_]] {
  //FIXME uuid is passed just for testing
  def startNewTrace(uuid: String): F[Unit]
}

object Tracing {
  implicit def instance[F[_]: Sync]: Tracing[F] = new Tracing[F] {
    override def startNewTrace(uuid: String): F[Unit] = {
      Sync[F].delay {
        MDC.put("X-TraceId", uuid)
      }
    }
  }
}
