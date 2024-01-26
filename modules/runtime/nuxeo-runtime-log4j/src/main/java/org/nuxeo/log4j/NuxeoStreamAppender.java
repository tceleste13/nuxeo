/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     bdelbosc
 */
package org.nuxeo.log4j;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.nuxeo.lib.stream.log.kafka.KafkaUtils.BOOTSTRAP_SERVERS_PROP;
import static org.nuxeo.lib.stream.log.kafka.KafkaUtils.DEFAULT_BOOTSTRAP_SERVERS;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.nuxeo.lib.stream.codec.AvroMessageCodec;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogAppender;
import org.nuxeo.lib.stream.log.Name;
import org.nuxeo.lib.stream.log.kafka.KafkaLogManager;

/**
 * A Log4j appender that writes to a Nuxeo Stream using Avro encoded event.
 *
 * @since 2023.7
 */
@Plugin(name = "NuxeoStreamAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class NuxeoStreamAppender extends AbstractAppender {

    private final KafkaLogManager manager;

    private final LogAppender<Record> appender;

    protected final String nodeId;

    protected static final String BOOTSTRAP_PROP = "bootstrap.servers";

    protected static final String BOOTSTRAP_AUTODETECT_VALUE = "autodetect";

    public static final Codec<NuxeoLogEvent> AVRO_CODEC = new AvroMessageCodec<>(NuxeoLogEvent.class);

    protected NuxeoStreamAppender(String name, Filter filter, Layout<? extends Serializable> layout,
            boolean ignoreExceptions, Property[] properties, String nodeId, String prefix, String streamName,
            int partitions) {
        super(name, filter, layout, ignoreExceptions, properties);
        Properties props = new Properties();
        for (Property prop : properties) {
            if (BOOTSTRAP_PROP.equals(prop.getName()) && BOOTSTRAP_AUTODETECT_VALUE.equals(prop.getValue())) {
                String bootstrapServers = System.getProperty(BOOTSTRAP_SERVERS_PROP, DEFAULT_BOOTSTRAP_SERVERS);
                if (bootstrapServers == null || bootstrapServers.isEmpty()) {
                    bootstrapServers = DEFAULT_BOOTSTRAP_SERVERS;
                }
                props.put(prop.getName(), bootstrapServers);
            } else {
                props.put(prop.getName(), prop.getValue());
            }
        }
        this.nodeId = nodeId;
        this.manager = new KafkaLogManager(prefix, props, props);
        Name stream = Name.ofUrn(streamName);
        manager.createIfNotExists(stream, partitions);
        this.appender = manager.getAppender(stream);
    }

    @PluginBuilderFactory
    public static <B extends Builder<B>> B newBuilder() {
        return (B) (new Builder()).asBuilder();
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        boolean stopped = super.stop(timeout, timeUnit, false);
        if (manager != null) {
            manager.close();
        }
        return stopped;
    }

    @Override
    public void append(LogEvent event) {
        NuxeoLogEvent nuxeoEvent = new NuxeoLogEvent(nodeId, event);
        Record rec = Record.of(Long.toString(nuxeoEvent.getThreadId()), AVRO_CODEC.encode(nuxeoEvent));
        appender.append(rec.getKey(), rec);
    }

    public static class Builder<B extends NuxeoStreamAppender.Builder<B>> extends AbstractAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<NuxeoStreamAppender> {
        private static final int DEFAULT_PARTITIONS = 1;

        @PluginBuilderAttribute
        @Required(message = "No stream provided for NuxeoStreamAppender")
        private String stream;

        @PluginBuilderAttribute
        @Required(message = "No prefix provided for NuxeoStreamAppender")
        private String prefix;

        @PluginBuilderAttribute
        private int partitions = DEFAULT_PARTITIONS;

        @PluginBuilderAttribute
        private String nodeId;

        public Builder() {
        }

        public NuxeoStreamAppender build() {
            return new NuxeoStreamAppender(this.getName(), this.getFilter(), this.getLayout(),
                    this.isIgnoreExceptions(), this.getPropertyArray(), this.getNodeId(), this.getPrefix(),
                    this.getStream(), this.getPartitions());
        }

        public String getStream() {
            return this.stream;
        }

        public B setStream(final String stream) {
            this.stream = stream;
            return this.asBuilder();
        }

        public String getPrefix() {
            return this.prefix;
        }

        public B setPrefix(final String prefix) {
            this.prefix = prefix;
            return this.asBuilder();
        }

        public int getPartitions() {
            return this.partitions;
        }

        public B setPartitions(int partitions) {
            this.partitions = partitions;
            return this.asBuilder();
        }

        public String getNodeId() {
            if (isBlank(nodeId)) {
                return getHostIp();
            }
            return nodeId;
        }

        public B setNodeId(String nodeId) {
            this.nodeId = nodeId;
            return this.asBuilder();
        }

        protected String getHostIp() {
            try {
                InetAddress host = InetAddress.getLocalHost();
                return host.getHostAddress();
            } catch (UnknownHostException e) {
                return "127.0.0.1";
            }
        }

    }
}
