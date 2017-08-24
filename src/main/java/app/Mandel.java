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

import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

public class Mandel {
    // Deliberately set a high limit to determine if a complex point is a
    // member of the mandelbrot set.  This makes the difference between
    // sequential and parallel execution observable when rendering
    final int limit = 1024 * 128;

    final double lr = -2.0;
    final double li = -1.0;
    final double ur = 1.0;
    final double ui = 1.0;

    final double h = ui - li;
    final double w = ur - lr;

    final int lines = 20;
    final int columns = (int) (w / h * lines * 2);

    final double stepr = w / columns;
    final double stepi = h / lines;

    public static void main(String[] args) {
        new Mandel().render();
    }

    public String render() {
        // Calculation of lines
        return IntStream.range(0, lines)
                // Calculate the imaginary c value
                .mapToDouble(l -> ui - (l * stepi))
                .mapToObj(this::renderLine)
                .collect(joining("\n", "|", "|"));
    }

    String renderLine(double ci) {
        // Sequential calculation of a line
        return IntStream.range(0, columns)
                // Calculate the real c value
                .mapToDouble(c -> lr + (c * stepr))
                // Calculate if (cr, ci) is a member of the set
                .mapToInt(cr -> isMemberOfMandelbrotSet(cr, ci))
                // Map to character
                .mapToObj(i -> i < (limit * 95 / 100) ? "*" : " ")
                // Join to represent line as a string
                .collect(joining());
    }

    //  z_n+1 = z_n^2 + c
    // returns 0 if a member or a number between 1 and limit, representing
    // the rate at which the function escapes to infinity
    int isMemberOfMandelbrotSet(double cr, double ci) {
        double zr = cr;
        double zi = ci;

        double zr2 = zr * zr;
        double zi2 = zi * zi;

        int i = limit;
        while (i > 0 && (zr2 + zi2) < 4.0) {
            i--;
            double _zr = zr2 - zi2 + cr;
            double _zi = 2.0 * zr * zi + ci;

            zr = _zr;
            zi = _zi;

            zr2 = zr * zr;
            zi2 = zi * zi;
        }
        return i;
    }
}
