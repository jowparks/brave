/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.kafka.streams;

import brave.Span;
import brave.Tracer;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;

import java.util.Collections;
import java.util.Map;

class TracingValueTransformerWithKey<K, V, VR> implements ValueTransformerWithKey<K, V, VR> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final Tracer tracer;
  final String spanName;
  final ValueTransformerWithKey<K, V, VR> delegateTransformer;
  final Map<Long,String> annotations;
  final Map<String, String> tags;

  ProcessorContext processorContext;

  TracingValueTransformerWithKey(KafkaStreamsTracing kafkaStreamsTracing, String spanName,
      ValueTransformerWithKey<K, V, VR> delegateTransformer) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracing.tracer();
    this.spanName = spanName;
    this.delegateTransformer = delegateTransformer;
    this.annotations = Collections.emptyMap();
    this.tags = Collections.emptyMap();
  }

  TracingValueTransformerWithKey(KafkaStreamsTracing kafkaStreamsTracing, String spanName,
      Map<Long, String> annotations, Map<String, String> tags,
      ValueTransformerWithKey<K, V, VR> delegateTransformer) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracing.tracer();
    this.spanName = spanName;
    this.delegateTransformer = delegateTransformer;
    this.annotations = Collections.emptyMap();
    this.tags = Collections.emptyMap();
  }

  @Override
  public void init(ProcessorContext processorContext) {
    this.processorContext = processorContext;
    delegateTransformer.init(processorContext);
  }

  @Override
  public VR transform(K k, V v) {
    Span span = kafkaStreamsTracing.nextSpan(processorContext);
    if (!span.isNoop()) {
      span.name(spanName);
      this.annotations.forEach(span::annotate);
      this.tags.forEach(span::tag);
      span.start();
    }

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return delegateTransformer.transform(k, v);
    } catch (RuntimeException | Error e) {
      span.error(e); // finish as an exception means the callback won't finish the span
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public void close() {
    delegateTransformer.close();
  }
}


