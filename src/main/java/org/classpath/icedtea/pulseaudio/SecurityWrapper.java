/* SecurityWrapper.java
   Copyright (C) 2014 Red Hat, Inc.

This file is part of IcedTea-Sound.

IcedTea-Sound is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 2.

IcedTea-Sound is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with IcedTea-Sound; see the file COPYING.  If not, write to
the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version.
*/

package org.classpath.icedtea.pulseaudio;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.classpath.icedtea.Config;

/**
 * A wrapper around the security sensitive functions
 *
 */
final class SecurityWrapper {

    static void loadNativeLibrary() {

        if (System.getSecurityManager() != null) {
            PrivilegedAction<Boolean> action = new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    System.loadLibrary(Config.PACKAGE_NAME + "-" + Config.PACKAGE_VERSION);
                    return true;
                }

            };

            AccessController.doPrivileged(action);

        } else {
            System.loadLibrary(Config.PACKAGE_NAME + "-" + Config.PACKAGE_VERSION);
        }

    }
}
