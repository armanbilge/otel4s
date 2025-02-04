/*
 * Copyright 2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.typelevel.otel4s
package sdk
package trace

import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.typelevel.otel4s.Attribute.KeySelect
import org.typelevel.otel4s.sdk.common.InstrumentationScope
import org.typelevel.otel4s.sdk.trace.data.EventData
import org.typelevel.otel4s.sdk.trace.data.LinkData
import org.typelevel.otel4s.sdk.trace.data.SpanData
import org.typelevel.otel4s.sdk.trace.data.StatusData
import org.typelevel.otel4s.sdk.trace.samplers.SamplingDecision
import org.typelevel.otel4s.trace.SpanContext
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.Status
import org.typelevel.otel4s.trace.TraceFlags
import org.typelevel.otel4s.trace.TraceState
import scodec.bits.ByteVector

object Gens {

  private val nonEmptyString: Gen[String] =
    Arbitrary.arbitrary[String].suchThat(_.nonEmpty)

  val attribute: Gen[Attribute[_]] = {
    implicit val stringArb: Arbitrary[String] =
      Arbitrary(nonEmptyString)

    implicit def listArb[A: Arbitrary]: Arbitrary[List[A]] =
      Arbitrary(Gen.nonEmptyListOf(Arbitrary.arbitrary[A]))

    def attribute[A: KeySelect: Arbitrary]: Gen[Attribute[A]] =
      for {
        key <- nonEmptyString
        value <- Arbitrary.arbitrary[A]
      } yield Attribute(key, value)

    val string: Gen[Attribute[String]] = attribute[String]
    val boolean: Gen[Attribute[Boolean]] = attribute[Boolean]
    val long: Gen[Attribute[Long]] = attribute[Long]
    val double: Gen[Attribute[Double]] = attribute[Double]

    val stringList: Gen[Attribute[List[String]]] = attribute[List[String]]
    val booleanList: Gen[Attribute[List[Boolean]]] = attribute[List[Boolean]]
    val longList: Gen[Attribute[List[Long]]] = attribute[List[Long]]
    val doubleList: Gen[Attribute[List[Double]]] = attribute[List[Double]]

    Gen.oneOf(
      boolean,
      string,
      long,
      double,
      stringList,
      booleanList,
      longList,
      doubleList
    )
  }

  val attributes: Gen[Attributes] =
    for {
      attributes <- Gen.listOf(attribute)
    } yield Attributes.fromSpecific(attributes)

  val instrumentationScope: Gen[InstrumentationScope] =
    for {
      name <- nonEmptyString
      version <- Gen.option(nonEmptyString)
      schemaUrl <- Gen.option(nonEmptyString)
      attributes <- Gens.attributes
    } yield InstrumentationScope(name, version, schemaUrl, attributes)

  val resource: Gen[Resource] =
    for {
      attributes <- Gens.attributes
      schemaUrl <- Gen.option(nonEmptyString)
    } yield Resource(attributes, schemaUrl)

  val samplingDecision: Gen[SamplingDecision] =
    Gen.oneOf(
      SamplingDecision.Drop,
      SamplingDecision.RecordOnly,
      SamplingDecision.RecordAndSample
    )

  val spanKind: Gen[SpanKind] =
    Gen.oneOf(
      SpanKind.Internal,
      SpanKind.Server,
      SpanKind.Client,
      SpanKind.Producer,
      SpanKind.Consumer
    )

  val status: Gen[Status] =
    Gen.oneOf(Status.Ok, Status.Error, Status.Unset)

  val traceId: Gen[ByteVector] =
    for {
      hi <- Gen.long
      lo <- Gen.long.suchThat(_ != 0)
    } yield SpanContext.TraceId.fromLongs(hi, lo)

  val spanId: Gen[ByteVector] =
    for {
      value <- Gen.long.suchThat(_ != 0)
    } yield SpanContext.SpanId.fromLong(value)

  val spanContext: Gen[SpanContext] =
    for {
      traceId <- Gens.traceId
      spanId <- Gens.spanId
      traceFlags <- Gen.oneOf(TraceFlags.Sampled, TraceFlags.Default)
      remote <- Gen.oneOf(true, false)
    } yield SpanContext(traceId, spanId, traceFlags, TraceState.empty, remote)

  val eventData: Gen[EventData] =
    for {
      name <- Gen.alphaNumStr
      epoch <- Gen.finiteDuration
      attributes <- Gens.attributes
    } yield EventData(name, epoch, attributes)

  val linkData: Gen[LinkData] =
    for {
      spanContext <- Gens.spanContext
      attributes <- Gens.attributes
    } yield LinkData(spanContext, attributes)

  val statusData: Gen[StatusData] =
    for {
      description <- Gen.option(Gen.alphaNumStr)
      data <- Gen.oneOf(
        StatusData.Ok,
        StatusData.Unset,
        StatusData.Error(description)
      )
    } yield data

  val spanData: Gen[SpanData] =
    for {
      name <- Gen.alphaStr
      spanContext <- Gens.spanContext
      parentSpanContext <- Gen.option(Gens.spanContext)
      kind <- Gens.spanKind
      startEpochNanos <- Gen.finiteDuration
      endEpochNanos <- Gen.option(Gen.finiteDuration)
      status <- Gens.statusData
      attributes <- Gens.attributes
      events <- Gen.listOf(Gens.eventData)
      links <- Gen.listOf(Gens.linkData)
      instrumentationScope <- Gens.instrumentationScope
      resource <- Gens.resource
    } yield SpanData(
      name,
      spanContext,
      parentSpanContext,
      kind,
      startEpochNanos,
      endEpochNanos,
      status,
      attributes,
      events.toVector,
      links.toVector,
      instrumentationScope,
      resource
    )
}
