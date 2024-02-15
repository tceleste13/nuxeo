/*
 * (C) Copyright 2013-2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Delbosc Benoit
 */
package org.nuxeo.runtime.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

import io.dropwizard.metrics5.Metric;
import io.dropwizard.metrics5.MetricAttribute;
import io.dropwizard.metrics5.MetricFilter;
import io.dropwizard.metrics5.MetricName;

@XObject("configuration")
public class MetricsConfigurationDescriptor implements Descriptor, MetricFilter {

    protected static final String ALL_METRICS = "ALL";

    protected static final FilterDescriptor NO_FILTER = new FilterDescriptor();

    @Override
    public String getId() {
        return UNIQUE_DESCRIPTOR_ID;
    }

    @XNode("@enabled")
    protected boolean isEnabled = true;

    /**
     * Returns the filter matching the name or null if not found.
     *
     * @since 2023.8
     */
    public FilterDescriptor getFilter(String name) {
        if (name == null) {
            return null;
        }
        return filters.stream().filter(f -> name.equals(f.getId())).findFirst().orElse(null);
    }

    /**
     * Returns the legacy or default filter.
     *
     * @since 2023.8
     */
    public FilterDescriptor getDefaultFilter() {
        // Legacy contrib contains a single filter without name
        FilterDescriptor ret = getFilter(UNIQUE_DESCRIPTOR_ID);
        if (ret != null) {
            return ret;
        }
        ret = getFilter(FilterDescriptor.DEFAULT_ID);
        if (ret != null) {
            return ret;
        }
        return NO_FILTER;
    }

    // @deprecated use directly filter implementation.
    @Deprecated(since = "2023.8", forRemoval = true)
    @Override
    public boolean matches(MetricName name, Metric metric) {
        return getDefaultFilter().matches(name, metric);
    }

    // @deprecated use directly filter implementation.
    @Deprecated(since = "2023.8", forRemoval = true)
    public Set<MetricAttribute> getDeniedExpansions() {
        return getDefaultFilter().getDeniedExpansions();
    }

    @XObject(value = "instrument")
    public static class InstrumentDescriptor implements Descriptor {

        @XNode("@name")
        protected String name;

        @XNode("@enabled")
        protected boolean isEnabled = true;

        @Override
        public String getId() {
            return name;
        }

        public boolean isEnabled() {
            return isEnabled;
        }
    }

    @XNodeList(value = "instrument", type = ArrayList.class, componentType = InstrumentDescriptor.class)
    protected List<InstrumentDescriptor> instruments = new ArrayList<>();

    @XObject(value = "filter")
    public static class FilterDescriptor implements Descriptor, MetricFilter {

        protected static final String DEFAULT_ID = "default";

        // @since 2023.8
        @XNode("@name")
        protected String name = UNIQUE_DESCRIPTOR_ID;

        @Override
        public String getId() {
            return name;
        }

        @XNodeList(value = "allow/prefix", type = ArrayList.class, componentType = String.class)
        protected List<String> allowedPrefix = new ArrayList<>();

        @XNodeList(value = "deny/prefix", type = ArrayList.class, componentType = String.class)
        protected List<String> deniedPrefix = new ArrayList<>();

        @XNodeList(value = "deny/expansion", type = ArrayList.class, componentType = String.class)
        protected List<String> deniedExpansions = new ArrayList<>();

        public List<String> getAllowedPrefix() {
            return Collections.unmodifiableList(allowedPrefix);
        }

        public List<String> getDeniedPrefix() {
            return Collections.unmodifiableList(deniedPrefix);
        }

        public Set<MetricAttribute> getDeniedExpansions() {
            if (deniedExpansions.isEmpty()) {
                return Collections.emptySet();
            }
            return deniedExpansions.stream()
                                   .map(expansion -> MetricAttribute.valueOf(expansion.toUpperCase().strip()))
                                   .collect(Collectors.toSet());
        }

        @Override
        public boolean matches(MetricName name, Metric metric) {
            String expandedName = expandName(name);
            return allowedPrefix.stream().anyMatch(f -> ALL_METRICS.equals(f) || expandedName.startsWith(f))
                    || deniedPrefix.stream().noneMatch(f -> ALL_METRICS.equals(f) || expandedName.startsWith(f));
        }

    }

    @XNodeList(value = "filter", type = ArrayList.class, componentType = FilterDescriptor.class)
    protected List<FilterDescriptor> filters = new ArrayList<>();

    public static String expandName(MetricName metric) {
        if (metric.getTags().isEmpty()) {
            return metric.getKey();
        }
        String name = metric.getKey();
        for (Map.Entry<String, String> entry : metric.getTags().entrySet()) {
            String key = "." + entry.getKey() + ".";
            String keyAndValue = key + entry.getValue() + ".";
            name = name.replace(key, keyAndValue);
        }
        return name;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public List<InstrumentDescriptor> getInstruments() {
        return instruments;
    }

}
