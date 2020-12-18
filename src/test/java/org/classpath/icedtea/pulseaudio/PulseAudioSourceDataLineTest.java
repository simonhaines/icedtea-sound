/* PulseSourceDataLineTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.MixerProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class PulseAudioSourceDataLineTest {
	Mixer mixer;
	SourceDataLine sourceDataLine;
	private int listenerCalled = 0;

	int started = 0;
	int stopped = 0;
	int opened = 0;
	int closed = 0;

	AudioFormat aSupportedFormat = new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44100f, 8, 1, 1, 44100f, true);

	class ThreadWriter extends Thread {
		SourceDataLine line;
		AudioInputStream stream;

		public ThreadWriter(AudioInputStream stream, SourceDataLine line) throws LineUnavailableException {

			this.line = line;
			this.stream = stream;

			if (line.isOpen()) {
				line.close();
			}

		}

		@Override
		public void run() {
			try {
				AudioFormat audioFormat = stream.getFormat();

				line.open(audioFormat);

				byte[] abData = new byte[1000];
				int bytesRead = 0;

				line.start();

				while (bytesRead >= 0) {
					bytesRead = stream.read(abData, 0, abData.length);
					// System.out.println("read data");
					if (bytesRead > 0) {
						// System.out.println("about to write data");
						line.write(abData, 0, bytesRead);
						// System.out.println("wrote data");
					}
				}

				line.drain();
				line.close();

			} catch (LineUnavailableException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@BeforeEach
	public void setUp() throws Exception {
		MixerProvider mixerProvider = new PulseAudioMixerProvider();
		Mixer.Info mixerInfos[] = mixerProvider.getMixerInfo();
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
		mixer = mixerProvider.getMixer(selectedMixerInfo);
		assertNotNull(mixer);
		if (mixer.isOpen()) {
			mixer.close();
		}

		mixer.open();

		sourceDataLine = null;

		started = 0;
		stopped = 0;
		opened = 0;
		closed = 0;
		listenerCalled = 0;
	}

	@Test
	public void testOpenAndClose() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		sourceDataLine.open();
		assertTrue(sourceDataLine.isOpen());

		sourceDataLine.close();
		assertFalse(sourceDataLine.isOpen());

	}

	@Test
	public void testIsActiveAndIsOpen() throws LineUnavailableException {

		sourceDataLine = (SourceDataLine) mixer
				.getLine(new DataLine.Info(SourceDataLine.class, aSupportedFormat, 1000));

		assertFalse(sourceDataLine.isActive());
		assertFalse(sourceDataLine.isOpen());
		sourceDataLine.open();
		assertTrue(sourceDataLine.isOpen());
		assertFalse(sourceDataLine.isActive());
		sourceDataLine.start();
		assertTrue(sourceDataLine.isOpen());
		assertTrue(sourceDataLine.isActive());
		sourceDataLine.stop();
		assertTrue(sourceDataLine.isOpen());
		assertFalse(sourceDataLine.isActive());
		sourceDataLine.close();
		assertFalse(sourceDataLine.isOpen());
		assertFalse(sourceDataLine.isActive());

	}

	@Test
	public void testPlay() throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		System.out.println("This test plays a file");

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		sourceDataLine.open(audioFormat);
		System.out.println("opened");
		sourceDataLine.start();
		System.out.println("started");
		byte[] abData = new byte[1000];
		int bytesRead = 0;

		while (bytesRead >= 0) {
			bytesRead = audioInputStream.read(abData, 0, abData.length);
			if (bytesRead > 0) {
				sourceDataLine.write(abData, 0, bytesRead);
			}
		}
		System.out.println("done");

		sourceDataLine.drain();
		System.out.println("drained");
		sourceDataLine.stop();
		sourceDataLine.close();
		System.out.println("closed");

	}

	@Test
	public void testWriteIntegralNumberFrames() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		/* try writing an non-integral number of frames size */
		sourceDataLine.open();
		int frameSize = sourceDataLine.getFormat().getFrameSize();
		byte[] buffer = new byte[(frameSize * 2) - 1];
		assertThrows(IllegalArgumentException.class, () -> sourceDataLine.write(buffer, 0, buffer.length));
	}

	@Test
	public void testWriteNegativeLength() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		sourceDataLine.open();
		int frameSize = sourceDataLine.getFormat().getFrameSize();
		byte[] buffer = new byte[(frameSize * 2)];
		/* try writing a negative length */
		assertThrows(IllegalArgumentException.class, () -> sourceDataLine.write(buffer, 0, -2));
	}

	@Test
	public void testWriteNegativeOffset() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		sourceDataLine.open();
		int frameSize = sourceDataLine.getFormat().getFrameSize();
		byte[] buffer = new byte[(frameSize * 2)];
		/* try writing with a negative offset */
		assertThrows(ArrayIndexOutOfBoundsException.class, () -> sourceDataLine.write(buffer, -1, buffer.length));
	}

	@Test
	public void testWriteMoreThanArrayLength() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		sourceDataLine.open();
		int frameSize = sourceDataLine.getFormat().getFrameSize();
		byte[] buffer = new byte[(frameSize * 2)];
		/* try writing more than the array length */
		assertThrows(ArrayIndexOutOfBoundsException.class, () -> sourceDataLine.write(buffer, 0, frameSize * 3));
	}

	@Test
	public void testWriteMoreThanArrayLength2() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		sourceDataLine.open();
		int frameSize = sourceDataLine.getFormat().getFrameSize();
		byte[] buffer = new byte[(frameSize * 2)];
		/* try writing more than the array length */
		assertThrows(ArrayIndexOutOfBoundsException.class, () -> sourceDataLine.write(buffer, 1, buffer.length));
	}

	// FIXME
	@Test
	public void testWriteWithoutStart()
			throws UnsupportedAudioFileException, IOException, LineUnavailableException, InterruptedException {

		System.out.println("This test doesnt play a file; you shouldnt hear anything");

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		final AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		Thread writer = new Thread() {
			@Override
			public void run() {
				try {
					sourceDataLine.open(audioFormat);
					byte[] abData = new byte[1000];
					int bytesRead = 0;
					int total = 0;

					while (bytesRead >= 0 && total < 50) {

						bytesRead = audioInputStream.read(abData, 0, abData.length);
						if (bytesRead > 0) {
							sourceDataLine.write(abData, 0, bytesRead);
						}

						// when the line is closed (in tearDown),
						// break out of the loop
						if (!sourceDataLine.isOpen()) {
							break;
						}
						total++;
					}
				} catch (LineUnavailableException e) {
					fail();
				} catch (IOException e) {
					fail();
				}
			}

		};

		writer.start();

		Thread.sleep(100);

		writer.join(2000);

		/* assert that the writer is still waiting in write */
		assertTrue(writer.isAlive());

	}

	@Test
	public void testWriteAndClose()
			throws UnsupportedAudioFileException, IOException, LineUnavailableException, InterruptedException {
		System.out.println("This test tries to close the line during a write");

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		sourceDataLine.open(audioFormat);
		sourceDataLine.start();

		Thread writer = new Thread() {

			@Override
			public void run() {
				try {
					final byte[] abData = new byte[10000000];

					int bytesRead = 0;
					while (bytesRead >= 0) {
						bytesRead = audioInputStream.read(abData, 0, abData.length);
						if (bytesRead > 0) {
							sourceDataLine.write(abData, 0, bytesRead);
						}
					}
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		};

		writer.start();
		Thread.sleep(100);

		sourceDataLine.close();

		writer.join(500);
		assertFalse(writer.isAlive());

	}

	@Test
	public void testWriteAndStop()
			throws UnsupportedAudioFileException, IOException, LineUnavailableException, InterruptedException {
		System.out.println("This test tries to stop the line during a write");

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		sourceDataLine.open(audioFormat);
		sourceDataLine.start();

		Thread writer = new Thread() {

			@Override
			public void run() {
				try {
					final byte[] abData = new byte[10000000];

					int bytesRead = 0;
					while (bytesRead >= 0) {
						bytesRead = audioInputStream.read(abData, 0, abData.length);
						if (bytesRead > 0) {
							sourceDataLine.write(abData, 0, bytesRead);
						}
					}
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		};

		writer.start();

		Thread.sleep(500);

		sourceDataLine.stop();

		writer.join(500);
		assertFalse(writer.isAlive());

		sourceDataLine.close();

	}

	@Test
	public void testWriteAndFlush()
			throws UnsupportedAudioFileException, IOException, LineUnavailableException, InterruptedException {

		System.out.println("This test tries to flush a line during a write");

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		sourceDataLine.open(audioFormat);
		sourceDataLine.start();

		Thread writer = new Thread() {

			@Override
			public void run() {
				try {
					final byte[] abData = new byte[10000000];

					int bytesRead = 0;
					while (bytesRead >= 0) {
						bytesRead = audioInputStream.read(abData, 0, abData.length);
						if (bytesRead > 0) {
							sourceDataLine.write(abData, 0, bytesRead);
						}
					}
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		};

		writer.start();

		Thread.sleep(100);

		sourceDataLine.flush();

		writer.join(500);
		assertFalse(writer.isAlive());

		sourceDataLine.stop();
		sourceDataLine.close();
	}

	@Test
	public void testStartedStopped() throws LineUnavailableException, UnsupportedAudioFileException, IOException {

		System.out.println("This test check START/STOP events. You should see 1 START and 1 STOP event");

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		sourceDataLine.open(audioFormat);

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

		sourceDataLine.addLineListener(startStopListener);

		byte[] abData = new byte[1000];
		int bytesRead = 0;

		sourceDataLine.start();
		while (bytesRead >= 0) {
			bytesRead = audioInputStream.read(abData, 0, abData.length);
			if (bytesRead > 0) {
				sourceDataLine.write(abData, 0, bytesRead);
			}
		}
		sourceDataLine.drain();

		sourceDataLine.stop();
		sourceDataLine.close();

		assertEquals(1, started);
		assertEquals(1, stopped);

	}

	@Test
	public void test2StartAndStopEvents()
			throws UnsupportedAudioFileException, IOException, LineUnavailableException, InterruptedException {

		System.out.println("This test checks if START and STOP notifications appear on corking");

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		LineListener startStopListener = new LineListener() {

			@Override
			public void update(LineEvent event) {
				if (event.getType() == LineEvent.Type.START) {
					System.out.println("START");
					started++;
				}

				if (event.getType() == LineEvent.Type.STOP) {
					System.out.println("STOP");
					stopped++;
				}
			}

		};

		sourceDataLine.addLineListener(startStopListener);
		System.out.println("Launching threadWriter");
		ThreadWriter writer = new ThreadWriter(audioInputStream, sourceDataLine);
		writer.start();
		// System.out.println("started");

		Thread.sleep(1000);

		sourceDataLine.stop();

		System.out.println("corked");

		Thread.sleep(5000);

		// UNCORK

		sourceDataLine.start();

		Thread.sleep(2000);

		// System.out.println("waiting for thread to finish");
		writer.join();

		assertEquals(2, started);
		assertEquals(2, stopped);

	}

	@Test
	public void test3StartAndStopEvents()
			throws UnsupportedAudioFileException, IOException, LineUnavailableException, InterruptedException {

		System.out.println("This test checks if START and STOP notifications appear on corking");

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		LineListener startStopListener = new LineListener() {

			@Override
			public void update(LineEvent event) {
				if (event.getType() == LineEvent.Type.START) {
					System.out.println("START");
					started++;
				}

				if (event.getType() == LineEvent.Type.STOP) {
					System.out.println("STOP");
					stopped++;
				}
			}

		};

		sourceDataLine.addLineListener(startStopListener);
		System.out.println("Launching threadWriter");
		ThreadWriter writer = new ThreadWriter(audioInputStream, sourceDataLine);
		writer.start();
		// System.out.println("started");

		Thread.sleep(1000);

		sourceDataLine.stop();

		Thread.sleep(1000);

		sourceDataLine.start();

		Thread.sleep(1000);

		sourceDataLine.stop();

		Thread.sleep(1000);

		sourceDataLine.start();

		Thread.sleep(1000);

		// System.out.println("waiting for thread to finish");
		writer.join();

		assertEquals(3, started);
		assertEquals(3, stopped);

	}

	@Test
	public void testStartOnClosedLine() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
		assertNotNull(sourceDataLine);

		assertThrows(IllegalStateException.class, () -> sourceDataLine.start());

	}

	@Test
	public void testStopOnClosedLine() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
		assertNotNull(sourceDataLine);

		assertThrows(IllegalStateException.class, () -> sourceDataLine.stop());
	}

	@Test
	public void testPlayLessThanFrameSize()
			throws LineUnavailableException, UnsupportedAudioFileException, IOException {

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();
		// the audio file must have an even number of channels
		assertTrue(audioFormat.getChannels() % 2 == 0);

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));

		byte[] data = new byte[1];
		data[0] = (byte) 'a';

		sourceDataLine.open();
		sourceDataLine.start();
		try {
			assertThrows(IllegalStateException.class, () -> sourceDataLine.write(data, 0, 1));
		} finally {
			sourceDataLine.stop();
			sourceDataLine.close();
		}
	}

	@Test
	public void testOpenFormat() throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		/*
		 * This test makes sure that the default format of a line using open() is the
		 * same format that was passed to the mixer's getLine() function to get the line
		 * in the first place
		 */

		final ClassLoader classLoader = getClass().getClassLoader();
		File soundFile = new File(classLoader.getResource("startup.wav").getFile());
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);
		sourceDataLine.open();
		assertTrue(sourceDataLine.getFormat().matches(audioFormat));
		sourceDataLine.close();
	}

	@Test
	public void testFindLineWithFormat() throws LineUnavailableException {
		System.out.println("This test tries to find a line with a valid format");

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, aSupportedFormat));
		sourceDataLine.open();
		System.out.println(sourceDataLine.getFormat());
		sourceDataLine.close();

	}

	@Test
	public void testFindLineWithWrongFormat() throws LineUnavailableException {
		System.out.println("This test tries to acquire a line with incorrect format spec");
		assertThrows(IllegalArgumentException.class, () -> {
			sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class,
					new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 44100, 10000, 1, 13, 10, true)));
			sourceDataLine.open();
			sourceDataLine.close();
		});
	}

	@Test
	public void testFindControl() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
		sourceDataLine.open();
		Control[] controls = sourceDataLine.getControls();
		assertNotNull(controls);
		assertTrue(sourceDataLine.getControls().length > 0);
		for (Control control : controls) {
			assertNotNull(control);
		}
		sourceDataLine.close();
	}

	@Test
	public void testSupportedControls() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
		sourceDataLine.open();
		assertTrue(sourceDataLine.isControlSupported(FloatControl.Type.VOLUME));
		sourceDataLine.close();
	}

	@Test
	public void testVolume() throws Exception {

		Mixer selectedMixer = mixer;
		sourceDataLine = (SourceDataLine) selectedMixer.getLine(new Line.Info(SourceDataLine.class));

		File soundFile = new File("logout.wav");
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine.open(audioFormat);
		sourceDataLine.start();
		FloatControl volume = (FloatControl) sourceDataLine.getControl(FloatControl.Type.VOLUME);

		volume.setValue(volume.getMaximum());

		byte[] abData = new byte[1000];
		int bytesRead = 0;

		while (bytesRead >= 0) {
			bytesRead = audioInputStream.read(abData, 0, abData.length);
			if (bytesRead > 0) {
				sourceDataLine.write(abData, 0, bytesRead);
			}
		}

		sourceDataLine.drain();
		sourceDataLine.close();
		selectedMixer.close();

	}

	@Test
	public void testVolumeChanging() throws LineUnavailableException, IOException, UnsupportedAudioFileException {

		Mixer selectedMixer = mixer;

		sourceDataLine = (SourceDataLine) selectedMixer.getLine(new Line.Info(SourceDataLine.class));

		File soundFile = new File("logout.wav");
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine.open(audioFormat);
		sourceDataLine.start();
		FloatControl volume = (FloatControl) sourceDataLine.getControl(FloatControl.Type.VOLUME);

		volume.setValue(volume.getMinimum());

		byte[] abData = new byte[1000];
		int bytesRead = 0;

		while (bytesRead >= 0) {
			bytesRead = audioInputStream.read(abData, 0, abData.length);
			if (bytesRead > 0) {
				sourceDataLine.write(abData, 0, bytesRead);
				volume.setValue(volume.getValue() + 100);
			}
		}

		sourceDataLine.drain();
		sourceDataLine.close();
		selectedMixer.close();

	}

	@Test
	public void testOpenEvent() throws LineUnavailableException {

		listenerCalled = 0;
		LineListener openListener = new LineListener() {
			public void update(LineEvent event) {
				assertTrue(event.getType() == LineEvent.Type.OPEN);
				PulseAudioSourceDataLineTest.this.listenerCalled++;
			}
		};

		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
		sourceDataLine.addLineListener(openListener);
		sourceDataLine.open();
		sourceDataLine.removeLineListener(openListener);
		sourceDataLine.close();
		assertEquals(1, listenerCalled);
		listenerCalled = 0;
	}

	@Test
	public void testCloseEvent() throws LineUnavailableException {
		listenerCalled = 0;
		LineListener closeListener = new LineListener() {
			public void update(LineEvent event) {
				assertTrue(event.getType() == LineEvent.Type.CLOSE);
				PulseAudioSourceDataLineTest.this.listenerCalled++;
			}
		};

		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
		sourceDataLine.open();
		sourceDataLine.addLineListener(closeListener);
		sourceDataLine.close();
		sourceDataLine.removeLineListener(closeListener);
		assertEquals(1, listenerCalled);
		listenerCalled = 0;
	}

	@Test
	public void testCloseEventWrongListener() throws LineUnavailableException {
		listenerCalled = 0;
		LineListener closeListener = new LineListener() {
			public void update(LineEvent event) {
				PulseAudioSourceDataLineTest.this.listenerCalled++;
			}
		};

		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		sourceDataLine.open();
		sourceDataLine.addLineListener(closeListener);
		sourceDataLine.removeLineListener(closeListener);
		sourceDataLine.close();
		assertEquals(0, listenerCalled);
		listenerCalled = 0;

	}

	@Test
	public void testFramePosition() throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		File soundFile = new File("testsounds/logout.wav");
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		sourceDataLine.open(audioFormat);
		sourceDataLine.start();

		byte[] abData = new byte[1000];
		int bytesRead = 0;

		while (bytesRead >= 0) {
			bytesRead = audioInputStream.read(abData, 0, abData.length);
			if (bytesRead > 0) {
				sourceDataLine.write(abData, 0, bytesRead);
			}
		}

		sourceDataLine.drain();
		sourceDataLine.stop();
		System.out.println("frame position: " + sourceDataLine.getFramePosition());
		long expected = 136703;
		long granularity = 100;
		long pos = sourceDataLine.getFramePosition();
		assertTrue(Math.abs(expected - pos) < granularity);
		sourceDataLine.close();
	}

	@Test
	public void testFramePositionAfterPlayingTwice()
			throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		File soundFile = new File("testsounds/logout.wav");
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		sourceDataLine.open(audioFormat);
		sourceDataLine.start();

		byte[] abData = new byte[1000];
		int bytesRead = 0;

		while (bytesRead >= 0) {
			bytesRead = audioInputStream.read(abData, 0, abData.length);
			if (bytesRead > 0) {
				sourceDataLine.write(abData, 0, bytesRead);
			}
		}

		sourceDataLine.drain();
		sourceDataLine.stop();

		soundFile = new File("testsounds/logout.wav");
		audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		audioFormat = audioInputStream.getFormat();

		sourceDataLine.start();

		abData = new byte[1000];
		bytesRead = 0;

		while (bytesRead >= 0) {
			bytesRead = audioInputStream.read(abData, 0, abData.length);
			if (bytesRead > 0) {
				sourceDataLine.write(abData, 0, bytesRead);
			}
		}

		sourceDataLine.drain();
		sourceDataLine.stop();

		System.out.println("frame position: " + sourceDataLine.getFramePosition());
		long expected = 136703 * 2;
		long granularity = 100;
		long pos = sourceDataLine.getFramePosition();
		assertTrue(Math.abs(expected - pos) < granularity);
		sourceDataLine.close();
	}

	@Test
	public void testMicroSecondPosition() throws UnsupportedAudioFileException, IOException, LineUnavailableException {

		File soundFile = new File("testsounds/logout.wav");
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		assertNotNull(sourceDataLine);

		sourceDataLine.open(audioFormat);
		sourceDataLine.start();

		byte[] abData = new byte[1000];
		int bytesRead = 0;

		while (bytesRead >= 0) {
			bytesRead = audioInputStream.read(abData, 0, abData.length);
			if (bytesRead > 0) {
				sourceDataLine.write(abData, 0, bytesRead);
			}
		}

		sourceDataLine.drain();
		sourceDataLine.stop();
		System.out.println("time position: " + sourceDataLine.getMicrosecondPosition());
		long expected = 6200;
		long granularity = 100;
		long pos = sourceDataLine.getMicrosecondPosition();
		assertTrue(Math.abs(expected - pos) < granularity);
		sourceDataLine.close();

	}

	@Test
	public void testBufferSizes() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
		sourceDataLine.open(aSupportedFormat, 10000);
		assertEquals(10000, sourceDataLine.getBufferSize());
		sourceDataLine.close();
	}

	@Test
	public void testHasADefaultFormat() throws LineUnavailableException {
		System.out.println("This test checks that a SourceDataLine has "
				+ " a default format, and it can be opened with" + " that format");

		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		/* check that there is a default format */
		assertNotNull(sourceDataLine.getFormat());
		System.out.println(sourceDataLine.getFormat());

		/* check that the line can be opened with the default format */
		sourceDataLine.open();
		sourceDataLine.close();

	}

	@Test
	public void testDefaultFormatWithGetLine() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer
				.getLine(new DataLine.Info(SourceDataLine.class, aSupportedFormat, 1000));
		assertEquals(aSupportedFormat, sourceDataLine.getFormat());

	}

	@Test
	public void testDefaultBufferSize() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer
				.getLine(new DataLine.Info(SourceDataLine.class, aSupportedFormat, 1000));
		assertEquals(StreamBufferAttributes.SANE_DEFAULT, sourceDataLine.getBufferSize());
	}

	@Test
	public void testDrainTwice() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer
				.getLine(new DataLine.Info(SourceDataLine.class, aSupportedFormat, 1000));

		sourceDataLine.open();
		sourceDataLine.drain();
		sourceDataLine.drain();
		sourceDataLine.close();

	}

	@Test
	public void testDrainWithoutStartDataOnTheLine()
			throws LineUnavailableException, UnsupportedAudioFileException, IOException, InterruptedException {

		File soundFile = new File("testsounds/logout.wav");
		final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat, 1000));
		assertNotNull(sourceDataLine);
		sourceDataLine.open();
		int available = sourceDataLine.available();
		assertTrue(available > 1000);

		try {
			final byte[] abData = new byte[2000];
			int bytesRead = 0;

			bytesRead = audioInputStream.read(abData, 0, abData.length);
			assertTrue(bytesRead > 0);
			sourceDataLine.write(abData, 0, bytesRead);
		} catch (IOException e) {
			System.out.println("Error");
		}

		Thread drainer = new Thread() {
			@Override
			public void run() {
				sourceDataLine.drain();
			}
		};

		drainer.start();

		drainer.join(1000);

		if (drainer.isAlive()) {
			sourceDataLine.close();
			drainer.join(1000);
			if (drainer.isAlive()) {
				fail("drain() does not return when the line has been closed");
			}
		} else {
			fail("drain() does not block when there is data on the " + "source data line and it hasnt been started");
		}

	}

	@Test
	public void testDrainWithoutStartNoDataOnTheLine()
			throws LineUnavailableException, UnsupportedAudioFileException, IOException, InterruptedException {

		File soundFile = new File("testsounds/logout.wav");
		final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		AudioFormat audioFormat = audioInputStream.getFormat();

		sourceDataLine = (SourceDataLine) mixer.getLine(new DataLine.Info(SourceDataLine.class, audioFormat, 1000));
		assertNotNull(sourceDataLine);
		sourceDataLine.open();
		int available = sourceDataLine.available();
		assertTrue(available > 1000);

		Thread drainer = new Thread() {
			@Override
			public void run() {
				sourceDataLine.drain();
			}
		};

		drainer.start();
		drainer.join(1000);

		if (drainer.isAlive()) {
			fail("drain() does not return when there is no data on a line that hasn't been started");
		}
	}

	@Test
	public void testDrainWithoutOpen() throws LineUnavailableException {
		assertThrows(IllegalStateException.class, () -> {
			sourceDataLine = (SourceDataLine) mixer
					.getLine(new DataLine.Info(SourceDataLine.class, aSupportedFormat, 1000));
			sourceDataLine.drain();
		});
	}

	@Test
	public void testFlushTwice() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer
				.getLine(new DataLine.Info(SourceDataLine.class, aSupportedFormat, 1000));

		sourceDataLine.open();
		sourceDataLine.flush();
		sourceDataLine.flush();
		sourceDataLine.close();

	}

	@Test
	public void testFlushWithoutOpen() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer
				.getLine(new DataLine.Info(SourceDataLine.class, aSupportedFormat, 1000));

		assertThrows(IllegalStateException.class, () -> sourceDataLine.flush());

	}

	@Test
	public void testFlushWithoutStart() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer
				.getLine(new DataLine.Info(SourceDataLine.class, aSupportedFormat, 1000));
		sourceDataLine.open();
		sourceDataLine.flush();

	}

	@Test
	public void testMixerKnowsAboutOpenLines() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		assertEquals(0, mixer.getSourceLines().length);
		sourceDataLine.open();
		assertEquals(1, mixer.getSourceLines().length);
		assertTrue(sourceDataLine == mixer.getSourceLines()[0]);
		sourceDataLine.close();
		assertEquals(0, mixer.getSourceLines().length);

	}

	@Test
	public void testMixerKnowsAboutOpen2Lines() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		assertEquals(0, mixer.getSourceLines().length);
		sourceDataLine.open(aSupportedFormat);
		assertEquals(1, mixer.getSourceLines().length);
		assertTrue(sourceDataLine == mixer.getSourceLines()[0]);
		sourceDataLine.close();
		assertEquals(0, mixer.getSourceLines().length);

	}

	@Test
	public void testMixerKnowsAboutOpen3Lines() throws LineUnavailableException {
		sourceDataLine = (SourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));

		assertEquals(0, mixer.getSourceLines().length);
		sourceDataLine.open(aSupportedFormat, 10000);
		assertEquals(1, mixer.getSourceLines().length);
		assertTrue(sourceDataLine == mixer.getSourceLines()[0]);
		sourceDataLine.close();
		assertEquals(0, mixer.getSourceLines().length);

	}

	@Test
	public void testAllSourceLinesClosed() {
		assertEquals(0, mixer.getSourceLines().length);

	}

	public static void debug(String string) {
		System.out.println("DEBUG: " + string);
	}

	@Disabled
	@Test
	public void testSynchronization() throws Exception {
		Mixer.Info mixerInfos[] = AudioSystem.getMixerInfo();
		Mixer.Info selectedMixerInfo = null;
		// int i = 0;
		for (Mixer.Info info : mixerInfos) {
			// System.out.println("Mixer Line " + i++ + ": " + info.getName() +
			// " " + info.getDescription());
			if (info.getName().contains("PulseAudio")) {
				selectedMixerInfo = info;
				System.out.println(selectedMixerInfo);
			}
		}

		PulseAudioMixer mixer = (PulseAudioMixer) AudioSystem.getMixer(selectedMixerInfo);

		mixer.open();
		final ClassLoader classLoader = getClass().getClassLoader();

		String fileName1 = "startup.wav";
		File soundFile1 = new File(classLoader.getResource(fileName1).getFile());
		AudioInputStream audioInputStream1 = AudioSystem.getAudioInputStream(soundFile1);

		PulseAudioSourceDataLine line1;
		line1 = (PulseAudioSourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
		line1.setName("Line 1");

		String fileName2 = "logout.wav";
		File soundFile = new File(classLoader.getResource(fileName2).getFile());
		AudioInputStream audioInputStream2 = AudioSystem.getAudioInputStream(soundFile);

		PulseAudioSourceDataLine line2;
		line2 = (PulseAudioSourceDataLine) mixer.getLine(new Line.Info(SourceDataLine.class));
		line2.setName("Line 2");

		ThreadWriter writer1 = new ThreadWriter(audioInputStream1, line1);
		ThreadWriter writer2 = new ThreadWriter(audioInputStream2, line2);
		// line2.start();
		// line1.start();

		Line[] lines = { line1, line2 };
		mixer.synchronize(lines, true);

		// line2.stop();

		debug("PulseAudioMixer: " + line1.getName() + " and " + line2.getName() + " synchronized");
		writer1.start();
		writer2.start();

		debug("PulseAudioMixer: writers started");
		line2.start();
		// line1.stop();
		// line1.start();
		debug("PulseAudioMixer: Started a line");

		writer1.join();
		writer2.join();

		debug("PulseAudioMixer: both lines joined");

		line2.close();
		debug("PulseAudioMixer: " + line2.getName() + " closed");

		line1.close();
		debug("PulseAudioMixer: " + line1.getName() + " closed");

		mixer.close();
		debug("PulseAudioMixer: mixer closed");

	}

	@AfterEach
	public void tearDown() throws Exception {
		started = 0;
		stopped = 0;

		if (sourceDataLine != null && sourceDataLine.isOpen()) {
			sourceDataLine.close();
		}

		if (mixer.isOpen()) {
			mixer.close();
		}

	}

}
