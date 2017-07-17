/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package app;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.common.TextFormat;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.logging.Logger;

@Path("/")
@Singleton
@Produces(MediaType.TEXT_PLAIN)
public class EndpointResource {
    private static final Logger EP_LOGGER = Logger.getLogger(EndpointResource.class.getName());

    enum State {
        ZOMBIE,
        ALIVE,
        READY;
    }

    static volatile State STATE = State.READY;


    // Get the host name

    @GET @Path("/host")
    public String host() throws Exception {
        EP_LOGGER.info("/host");

        return InetAddress.getLocalHost().getHostName();
    }

    // So some work

    @GET @Path("/work")
    public String work() throws Exception {
        EP_LOGGER.info("/work");

        Histogram.Timer timer = Metrics.WORK_REQUEST_LATENCY.startTimer();
        try {
            return new Mandel().render();
        }
        finally {
            timer.observeDuration();
        }
    }

    // Metrics

    @GET @Path("/metrics")
    public String Metrics() throws IOException {
        EP_LOGGER.info("/metrics");

        StringWriter writer = new StringWriter();
        TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
        return writer.toString();
    }

    // Probes and lifecycle

    @Path("probe")
    public Probe probe() {
        return new Probe();
    }

    @Path("lifecycle")
    public Lifecycle lifecycle() {
        return new Lifecycle();
    }

    @Produces(MediaType.TEXT_PLAIN)
    static public class Probe {
        @POST
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public String probeUpdate(@FormParam("state") String s) {
            EP_LOGGER.info("/probe");

            State prev = STATE;
            STATE = State.valueOf(s);
            return prev.name();
        }

        @GET @Path("liveness")
        public Response liveness() {
            EP_LOGGER.info("/probe/liveness");

            State s = STATE;
            switch (s) {
                case ZOMBIE:
                    return Response.serverError().entity(s.toString()).build();
                default:
                    return Response.ok(State.ALIVE.toString()).build();
            }
        }

        @GET @Path("readiness")
        public Response readiness() {
            EP_LOGGER.info("/probe/readiness");

            State s = STATE;
            switch (s) {
                case READY:
                    return Response.ok(State.READY.toString()).build();
                default:
                    return Response.serverError().entity(s.toString()).build();
            }
        }
    }

    @Produces(MediaType.TEXT_PLAIN)
    static public class Lifecycle {
        @GET @Path("postStart")
        public Response postStart() {
            EP_LOGGER.info("/liveness/postStart");
            return Response.ok().build();
        }

        @GET @Path("preStop")
        public Response preStop() {
            EP_LOGGER.info("/liveness/preStop");

            App.FINISH.countDown();

            return Response.ok().build();
        }
    }
}