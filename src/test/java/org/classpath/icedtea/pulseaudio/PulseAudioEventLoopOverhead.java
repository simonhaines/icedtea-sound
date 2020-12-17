/* PulseAudioEventLoopOverhead.java
   Copyright (C) 2008 Red Hat, Inc.

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

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

public class PulseAudioEventLoopOverhead {

        Mixer mixer;

        @BeforeEach
        public void setUp() {
                Mixer.Info mixerInfos[] = AudioSystem.getMixerInfo();
                Mixer.Info selectedMixerInfo = null;
                // int i = 0;
                for (Mixer.Info info : mixerInfos) {
                        // System.out.println("Mixer Line " + i++ + ": " + info.getName() +
                        // " " + info.getDescription());
                        if (info.getName().contains("PulseAudio")) {
                                selectedMixerInfo = info;
                        }
                }
                assertNotNull(selectedMixerInfo);
                mixer = AudioSystem.getMixer(selectedMixerInfo);
                assertNotNull(mixer);
                if (mixer.isOpen()) {
                        mixer.close();
                }

        }

        @Test
        @Disabled
        public void testLongWait() throws LineUnavailableException {
                mixer.open();
                try {
                        /*
                         * While this test is running, the java procces shouldnt be hogging
                         * the cpu at all
                         */

                        Thread.sleep(100000);
                } catch (InterruptedException e) {
                        System.out.println("thread interrupted");
                }

        }

        @AfterEach
        public void tearDown() {
                if (mixer.isOpen()) {
                        mixer.close();
                }
        }

}
