package uk.gov.hmrc.agentfirelationship.support

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.scalatest.{Assertion, Matchers, Suite}
import play.api.Application

import scala.collection.JavaConverters._

trait MetricTestSupport {
  self: Suite with Matchers =>

  def app: Application

  private var metricsRegistry: MetricRegistry = _

  def givenCleanMetricRegistry(): Unit = {
    val registry = app.injector.instanceOf[Metrics].defaultRegistry
    for (metric <- registry.getMetrics.keySet().iterator().asScala) {
      registry.remove(metric)
    }
    metricsRegistry = registry
  }

  def timerShouldExistsAndBeenUpdated(metric: String): Assertion = {
    metricsRegistry.getTimers.get(s"Timer-$metric").getCount should be >= 1L
  }

}
