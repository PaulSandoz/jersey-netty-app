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

import io.netty.channel.Channel;
import io.prometheus.client.hotspot.DefaultExports;
import org.glassfish.jersey.netty.httpserver.NettyHttpContainerProvider;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class App {

    private static final URI BASE_URI = URI.create("http://localhost:8080/");

    private static final Logger APP_LOGGER = Logger.getLogger(App.class.getName());

    static final CountDownLatch FINISH = new CountDownLatch(1);

    public static void main(String[] args) {
        APP_LOGGER.info("Max memory " + Runtime.getRuntime().maxMemory() / (1 << 20));

        APP_LOGGER.info("Prometheus intializing JVM metrics");

        DefaultExports.initialize();

        APP_LOGGER.info("Netty starting");

        ResourceConfig config = new ResourceConfig(EndpointResource.class);
        Channel server = NettyHttpContainerProvider.createHttp2Server(BASE_URI, config, null);

        Runtime.getRuntime().addShutdownHook(new Thread(server::close));

        APP_LOGGER.info("Netty started");

        try {
            FINISH.await();
        }
        catch (InterruptedException ex) {
        }
        finally {
            APP_LOGGER.info("Netty shutting down");
            server.close();
        }
    }
}
