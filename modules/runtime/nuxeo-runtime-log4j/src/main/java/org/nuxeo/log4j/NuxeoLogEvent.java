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

import java.io.PrintWriter;
import java.io.Serial;
import java.io.Serializable;
import java.io.StringWriter;

import org.apache.avro.reflect.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

/**
 * An object representing a log4j event that can be Avro serialized.
 *
 * @since 2023.7
 */
public class NuxeoLogEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 20240126L;

    protected static final int MAX_MESSAGE_SIZE = 48_000;

    protected static final String TRUNCATE_PREFIX = "TRUNCATED: ";

    protected static final String TRUNCATE_SUFFIX = "...";

    protected static final int MAX_THROWN_SIZE = 64_000;

    protected long instantEpochSecond;

    protected long instantNanoOfSecond;

    protected String message;

    protected int level;

    protected String loggerName;

    protected String loggerFqcn;

    protected String nodeId;

    @Nullable
    protected String thrown;

    protected long threadId;

    @Nullable
    protected String threadName;

    @Nullable
    protected String pathInfo;

    @Nullable
    protected String userPrincipal;

    protected NuxeoLogEvent() {
        // Empty constructor for Avro decoder
    }

    public NuxeoLogEvent(String nodeId, LogEvent event) {
        setMessage(event.getMessage().getFormattedMessage());
        setInstantEpochSecond(event.getInstant().getEpochSecond());
        setInstantNanoOfSecond(event.getInstant().getNanoOfSecond());
        setLevel(event.getLevel().intLevel());
        setLoggerName(event.getLoggerName());
        setLoggerFqcn(event.getLoggerFqcn());
        setThreadId(event.getThreadId());
        setThreadName(event.getThreadName());
        setNodeId(nodeId);
        if (event.getThrown() != null) {
            StringWriter errors = new StringWriter();
            event.getThrown().printStackTrace(new PrintWriter(errors));
            setThrown(errors.toString());
        }
        ReadOnlyStringMap contextMap = event.getContextData();
        if (contextMap.containsKey("UserPrincipal")) {
            setUserPrincipal(contextMap.getValue("UserPrincipal"));
        }
        if (contextMap.containsKey("PathInfo")) {
            setPathInfo(contextMap.getValue("PathInfo"));
        }
    }

    public long getInstantEpochSecond() {
        return instantEpochSecond;
    }

    public void setInstantEpochSecond(long instantEpochSecond) {
        this.instantEpochSecond = instantEpochSecond;
    }

    public long getInstantNanoOfSecond() {
        return instantNanoOfSecond;
    }

    public void setInstantNanoOfSecond(long instantNanoOfSecond) {
        this.instantNanoOfSecond = instantNanoOfSecond;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        if (message != null && message.length() > MAX_MESSAGE_SIZE) {
            this.message = TRUNCATE_PREFIX + message.substring(0, MAX_MESSAGE_SIZE) + TRUNCATE_SUFFIX;
        } else {
            this.message = message;
        }
    }

    public String getThrown() {
        return thrown;
    }

    public void setThrown(String thrown) {
        if (thrown != null && thrown.length() > MAX_THROWN_SIZE) {
            this.message = TRUNCATE_PREFIX + thrown.substring(0, MAX_THROWN_SIZE) + TRUNCATE_SUFFIX;
        } else {
            this.thrown = thrown;
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

    public String getLoggerFqcn() {
        return loggerFqcn;
    }

    public void setLoggerFqcn(String loggerFqcn) {
        this.loggerFqcn = loggerFqcn;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getPathInfo() {
        return pathInfo;
    }

    public void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }

    public String getUserPrincipal() {
        return userPrincipal;
    }

    public void setUserPrincipal(String userPrincipal) {
        this.userPrincipal = userPrincipal;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
