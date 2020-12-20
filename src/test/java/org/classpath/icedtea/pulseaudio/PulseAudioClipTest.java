/* PulseAudioClipTest.java
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

import static java.time.Duration.ofMillis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.MixerProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PulseAudioClipTest {

	Mixer mixer;
	AudioFormat aSupportedFormat = new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44100f, 8, 1, 1, 44100f, true);

	int started = 0;
	int stopped = 0;
	int opened = 0;
	int closed = 0;

	@BeforeEach
	public void setUp() throws LineUnavailableException {
		MixerProvider mixerProvider = new PulseAudioMixerProvider();
		Mixer.Info wantedMixerInfo = null;
		Mixer.Info[] mixerInfos = mixerProvider.getMixerInfo();
		for (Mixer.Info mixerInfo : mixerInfos) {
			if (mixerInfo.getName().contains("PulseAudio")) {
				wantedMixerInfo = mixerInfo;
				break;
			}
		}
		assert (wantedMixerInfo != null);
		mixer = mixerProvider.getMixer(wantedMixerInfo);
		mixer.open();

		started = 0;
		stopped = 0;
		opened = 0;
		closed = 0;
	}

	@Test
	public void testObtainingAClip() throws LineUnavailableException {
		System.out.println("This tests if a clip can be obtained from the mixer");
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		assertNotNull(clip);
	}

	@Test
	public void testClipOpenWrongUse() throws LineUnavailableException {
		assertThrows(IllegalArgumentException.class, () -> {
			Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
			clip.open();
		});
	}

	@Test
	public void testClipOpens() throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		clip.open(audioInputStream);
		clip.close();
	}

	@Test
	public void testOpenCloseLotsOfTimes() throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		for (int i = 0; i < 1000; i++) {
			Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
			clip.open(audioInputStream);
			clip.close();
		}
	}

	@Test
	public void testLoop4Times() throws LineUnavailableException, IOException, UnsupportedAudioFileException {
		System.out.println(
				"This tests loop(4) on the Clip. " + "You should hear a certain part of the clip play back 5 time");

		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		clip.open(audioInputStream);

		clip.setLoopPoints((int) (clip.getFrameLength() / 4), (int) (clip.getFrameLength() / 2));
		clip.loop(4);

		clip.drain();

		clip.stop();

		clip.close();
	}

	@Test
	public void testLoopContinuously()
			throws LineUnavailableException, IOException, UnsupportedAudioFileException, InterruptedException {
		System.out.println("This tests loop(LOOP_CONTINUOUSLY) on the Clip");
		final Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("error.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		clip.open(audioInputStream);

		clip.setLoopPoints((int) (clip.getFrameLength() / 4), (int) (clip.getFrameLength() / 2));
		clip.loop(Clip.LOOP_CONTINUOUSLY);

		Runnable blocker = new Runnable() {

			@Override
			public void run() {
				clip.drain();
			}

		};

		Thread th = new Thread(blocker);
		th.start();
		th.join(10000);

		if (!th.isAlive()) {
			clip.close();
			fail("LOOP_CONTINUOUSLY doesnt seem to work");
		}

		clip.stop();
		th.join(500);
		if (th.isAlive()) {
			clip.close();
			fail("stopping LOOP_CONTINUOSLY failed");
		}

		clip.close();
	}

	@Test
	public void testIsActiveAndIsOpen() throws LineUnavailableException, UnsupportedAudioFileException, IOException {

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));

		assertFalse(clip.isActive());
		assertFalse(clip.isOpen());
		clip.open(audioInputStream);
		assertTrue(clip.isOpen());
		assertFalse(clip.isActive());
		clip.start();
		assertTrue(clip.isOpen());
		assertTrue(clip.isActive());
		clip.stop();
		assertTrue(clip.isOpen());
		assertFalse(clip.isActive());
		clip.close();
		assertFalse(clip.isOpen());
		assertFalse(clip.isActive());

	}

	@Test
	public void testOpenEvent() throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		System.out.println("This tests the OPEN event. You should see an OPEN on the next line");
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);

		opened = 0;

		LineListener openListener = new LineListener() {

			@Override
			public void update(LineEvent event) {
				if (event.getType() == LineEvent.Type.OPEN) {
					System.out.println("OPEN");
					opened++;
				}
			}

		};

		clip.addLineListener(openListener);
		clip.open(audioInputStream);
		clip.close();
		clip.removeLineListener(openListener);

		assertEquals(1, opened);

	}

	@Test
	public void testCloseEvent() throws LineUnavailableException, UnsupportedAudioFileException, IOException {

		System.out.println("This tests the CLOSE event");

		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);

		closed = 0;

		LineListener closeListener = new LineListener() {

			@Override
			public void update(LineEvent event) {
				if (event.getType() == LineEvent.Type.CLOSE) {
					System.out.println("CLOSE");
					closed++;
				}
			}

		};

		clip.addLineListener(closeListener);
		clip.open(audioInputStream);
		clip.close();

		clip.removeLineListener(closeListener);

		assertEquals(1, closed);

	}

	@Test
	public void testStartedStopped()
			throws LineUnavailableException, UnsupportedAudioFileException, IOException, InterruptedException {

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		Clip clip;
		clip = (Clip) mixer.getLine(new DataLine.Info(Clip.class, audioFormat));
		assertNotNull(clip);

		clip.open(audioInputStream);

		LineListener startStopListener = new LineListener() {

			@Override
			public void update(LineEvent event) {
				if (event.getType() == LineEvent.Type.START) {
					System.out.println("START");
					started++;
					assertEquals(1, started);
				}

				if (event.getType() == LineEvent.Type.STOP) {
					System.out.println("STOP");
					stopped++;
					assertEquals(1, stopped);
				}
			}

		};

		clip.addLineListener(startStopListener);

		clip.start();
		clip.drain();
		clip.stop();
		clip.close();

		assertEquals(1, started);
		assertEquals(1, stopped);

	}

	@Test
	public void testDrainWithoutStart() throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		assertTimeout(ofMillis(1000), () -> {
			final ClassLoader classLoader = getClass().getClassLoader();
			File soundFile = new File(classLoader.getResource("startup.wav").getFile());
			AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
			AudioFormat audioFormat = audioInputStream.getFormat();

			Clip clip;
			clip = (Clip) mixer.getLine(new DataLine.Info(Clip.class, audioFormat));
			assertNotNull(clip);

			clip.open(audioInputStream);
			clip.drain();
			clip.close();
		});
	}

	@Test
	public void testDrainBlocksWhilePlaying()
			throws UnsupportedAudioFileException, IOException, LineUnavailableException {

		final String fileName = "startup.wav";
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource(fileName).getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		Clip clip;
		clip = (Clip) mixer.getLine(new DataLine.Info(Clip.class, audioFormat));
		assertNotNull(clip);

		long startTime = System.currentTimeMillis();

		clip.open(audioInputStream);
		clip.start();
		clip.drain();
		clip.stop();
		clip.close();

		long endTime = System.currentTimeMillis();

		assertTrue(endTime - startTime > 3000);
		System.out.println("Playback of " + fileName + " completed in " + (endTime - startTime) + " milliseconds");
	}

	@Test
	public void testLoop0InterruptsPlayback()
			throws LineUnavailableException, IOException, UnsupportedAudioFileException {
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		clip.open(audioInputStream);

		clip.setLoopPoints((int) (clip.getFrameLength() / 4), (int) (clip.getFrameLength() / 2));
		clip.loop(4);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		clip.loop(0);
		clip.close();
	}

	@Test
	public void testOpenWrongUse() throws LineUnavailableException {
		assertThrows(IllegalArgumentException.class, () -> {
			Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
			clip.open();
		});
	}

	/*
	 *
	 * modified version of the sample code at
	 * http://icedtea.classpath.org/bugzilla/show_bug.cgi?id=173
	 *
	 */

	@Test
	public void testPlayTwoClips() throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile1 = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream1 = AudioSystem.getAudioInputStream(soundFile1);
		Clip clip1 = (Clip) mixer.getLine(new Line.Info(Clip.class));
		clip1.open(audioInputStream1);

		File soundFile2 = new File(classLoader.getResource("logout.wav").getFile());
		AudioInputStream audioInputStream2 = AudioSystem.getAudioInputStream(soundFile2);
		Clip clip2 = (Clip) mixer.getLine(new Line.Info(Clip.class));
		clip2.open(audioInputStream2);

		clip1.start();
		clip2.start();

		clip1.drain();
		clip2.drain();

		clip1.close();
		clip2.close();

		assertFalse(clip1.isOpen());
		assertFalse(clip2.isOpen());

	}

	@Test
	public void testSupportedControls() throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile1 = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream1 = AudioSystem.getAudioInputStream(soundFile1);
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		clip.open(audioInputStream1);

		Control[] controls = clip.getControls();
		assertNotNull(controls);
		assertTrue(controls.length >= 1);
		for (Control control : controls) {
			assertNotNull(control);
		}

		clip.close();
	}

	@Test
	public void testFramePositionBeforeClose()
			throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		System.out.println("This tests if the Clip provides the correct frame position");
		final ClassLoader classLoader = getClass().getClassLoader();
		String fileName = "logout.wav";
		File soundFile1 = new File(classLoader.getResource(fileName).getFile());
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));

		AudioInputStream audioInputStream1 = AudioSystem.getAudioInputStream(soundFile1);
		clip.open(audioInputStream1);

		clip.start();

		clip.drain();

		long pos = clip.getFramePosition();

		clip.close();

		long expected = 136703;
		long granularity = 5;
		System.out.println("Frames in " + fileName + ": " + expected);
		System.out.println("Frame position in clip :" + pos);
		assertTrue(Math.abs(expected - pos) < granularity, "Expected: " + expected + " got " + pos);

	}

	@Test
	public void testFramePositionWithStartStop()
			throws LineUnavailableException, UnsupportedAudioFileException, IOException, InterruptedException {

		System.out.println("This tests if the Clip provides the correct frame position");

		final ClassLoader classLoader = getClass().getClassLoader();
		String fileName = "logout.wav";
		File soundFile1 = new File(classLoader.getResource(fileName).getFile());
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		AudioInputStream audioInputStream1 = AudioSystem.getAudioInputStream(soundFile1);
		clip.open(audioInputStream1);

		clip.start();

		Thread.sleep(500);
		clip.stop();
		Thread.sleep(5000);
		clip.start();

		clip.drain();

		long pos = clip.getFramePosition();

		clip.close();

		long expected = 136703;
		long granularity = 5;
		System.out.println("Frames in " + fileName + ": " + expected);
		System.out.println("Frame position in clip :" + pos);
		assertTrue(Math.abs(expected - pos) < granularity, "Expected: " + expected + " got " + pos);

	}

	@Test
	public void testFramePositionWithLoop()
			throws LineUnavailableException, UnsupportedAudioFileException, IOException, InterruptedException {
		System.out.println(
				"This tests if the Clip provides the correct frame " + "position with a bit of looping in the clip");

		final ClassLoader classLoader = getClass().getClassLoader();
		String fileName = "logout.wav";
		File soundFile1 = new File(classLoader.getResource(fileName).getFile());
		AudioInputStream audioInputStream1 = AudioSystem.getAudioInputStream(soundFile1);
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		clip.open(audioInputStream1);

		clip.setLoopPoints(0, -1);
		clip.loop(1);
		Thread.sleep(500);
		clip.stop();
		Thread.sleep(2000);
		clip.start();
		Thread.sleep(100);
		clip.stop();
		Thread.sleep(4000);
		clip.start();
		Thread.sleep(100);
		clip.stop();
		Thread.sleep(2000);
		clip.start();
		Thread.sleep(100);
		clip.stop();
		Thread.sleep(3000);
		clip.start();

		clip.drain();

		long pos = clip.getFramePosition();

		clip.close();

		long expected = 136703 * 1;
		long granularity = 5;
		System.out.println("Frames in " + fileName + ": " + expected);
		System.out.println("Frame position in clip :" + pos);
		assertTrue(Math.abs(expected - pos) < granularity, "Expected: " + expected + " got " + pos);

	}

	@Test
	public void testFramePositionAfterLooping()
			throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		System.out.println("This tests if the Clip provides the correct frame position");
		final ClassLoader classLoader = getClass().getClassLoader();
		String fileName = "logout.wav";
		File soundFile1 = new File(classLoader.getResource(fileName).getFile());
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		AudioInputStream audioInputStream1 = AudioSystem.getAudioInputStream(soundFile1);
		clip.open(audioInputStream1);

		clip.setLoopPoints(0, -1);
		clip.loop(1);

		clip.drain();

		long pos = clip.getFramePosition();

		clip.close();

		long expected = 136703 * 2;
		long granularity = 5;
		System.out.println("Frames in " + fileName + ": " + expected);
		System.out.println("Frame position in clip :" + pos);
		assertTrue(Math.abs(expected - pos) < granularity, "Expected: " + expected + " got " + pos);

	}

	@Test
	public void testMixerKnowsAboutOpenClips()
			throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		Clip clip = (Clip) mixer.getLine(new Line.Info(Clip.class));
		final ClassLoader classLoader = getClass().getClassLoader();
		String fileName = "startup.wav";
		File soundFile1 = new File(classLoader.getResource(fileName).getFile());
		AudioInputStream audioInputStream1 = AudioSystem.getAudioInputStream(soundFile1);

		int initiallyOpenClips = mixer.getSourceLines().length;
		assertEquals(initiallyOpenClips, mixer.getSourceLines().length);
		clip.open(audioInputStream1);
		assertEquals(initiallyOpenClips + 1, mixer.getSourceLines().length);
		assertEquals(clip, mixer.getSourceLines()[initiallyOpenClips]);
		clip.close();
		assertEquals(initiallyOpenClips, mixer.getSourceLines().length);

	}

	@AfterEach
	public void tearDown() {
		mixer.close();

	}

}
