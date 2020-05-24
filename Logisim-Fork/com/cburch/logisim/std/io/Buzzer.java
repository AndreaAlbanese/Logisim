package com.cburch.logisim.std.io;

import java.awt.Color;
import java.awt.Graphics;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.GraphicsUtil;

public class Buzzer extends InstanceFactory {
	private static class Data implements InstanceData {
		private AtomicBoolean is_on = new AtomicBoolean(false);
		private final int SAMPLE_RATE = 80000;
		private int hz = 523;
		private float vol = 12;
		private Thread thread;

		public Data() {
			StartThread();
		}

		@Override
		public Object clone() {
			return new Data();
		}

		public void StartThread() {
			// avoid crash (for example if you connect a clock at 4KHz to the enable pin)
			if (Thread.activeCount() > 100)
				return;
			thread = new Thread(new Runnable() {
				@Override
				public void run() {
					AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, true);
					SourceDataLine line = null;
					try {
						line = AudioSystem.getSourceDataLine(format);
						line.open(format, SAMPLE_RATE / 10);
					} catch (Exception e) {
						e.printStackTrace();
						System.err.println("Could not initialise audio");
						return;
					}
					line.start();
					byte[] audioData = new byte[1];
					int framesPerWavelength = Math.round(SAMPLE_RATE / (float) hz);
					int oldHz = hz;
					while (is_on.get()) {
						if (hz > 0 && vol > 0) {
							for (int i = 0; i < framesPerWavelength; i++) {
								if (oldHz != hz) {
									float WavePoint = i / (float) framesPerWavelength;
									framesPerWavelength = Math.round(SAMPLE_RATE / (float) hz);
									i = Math.round(framesPerWavelength * WavePoint);
									oldHz = hz;
								}
								audioData[0] = (byte) Math.round(Math.sin(2 * Math.PI * i / framesPerWavelength) * vol);
								line.write(audioData, 0, 1);
							}
						}
					}
					line.stop();
					line.drain();
					line.close();
				}
			});
			thread.start();
			thread.setName("Sound Thread");
		}
	}

	private static final byte FREQ = 0;
	private static final byte ENABLE = 1;
	private static final byte VOL = 2;
	private static final Attribute<BitWidth> VOLUME_WIDTH = Attributes.forBitWidth("vol_width",
			Strings.getter("buzzerVolumeBitWidth"));

	private static final AttributeOption Hz = new AttributeOption("Hz", Strings.getter("Hz"));
	private static final AttributeOption dHz = new AttributeOption("dHz", Strings.getter("dHz (0.1Hz)"));
	private static final Attribute<AttributeOption> FREQUENCY_MEASURE = Attributes.forOption("freq_measure",
			Strings.getter("buzzerFrequecy"), new AttributeOption[] { Hz, dHz });

	public static void StopBuzzerSound(Component comp, CircuitState circState) {
		// static method, have to check if the comp parameter is a Buzzer or contains it
		ComponentFactory compFact = comp.getFactory();
		// if it is a buzzer, stop its sound thread
		if (compFact instanceof Buzzer) {
			Data d = (Data) circState.getData(comp);
			if (d != null && d.thread.isAlive()) {
				d.is_on.set(false);
			}
		}
		// if it's a subcircuit search other buzzer's instances inside it and stop all
		// sound threads
		else if (compFact instanceof SubcircuitFactory) {
			for (Component subComponent : ((SubcircuitFactory) comp.getFactory()).getSubcircuit().getComponents()) {
				// recursive if there are other subcircuits
				StopBuzzerSound(subComponent, ((SubcircuitFactory) compFact).getSubstate(circState, comp));
			}
		}
	}

	public Buzzer() {
		super("Buzzer", Strings.getter("buzzerComponent"));
		setAttributes(
				new Attribute[] { StdAttr.FACING, FREQUENCY_MEASURE, VOLUME_WIDTH, StdAttr.LABEL, Io.ATTR_LABEL_LOC,
						StdAttr.LABEL_FONT, StdAttr.ATTR_LABEL_COLOR },
				new Object[] { Direction.WEST, Hz, BitWidth.create(7), "", Direction.NORTH, StdAttr.DEFAULT_LABEL_FONT,
						Color.BLACK });
		setFacingAttribute(StdAttr.FACING);
		setIconName("buzzer.gif");
	}

	private void computeTextField(Instance instance) {
		Direction facing = instance.getAttributeValue(StdAttr.FACING);
		Object labelLoc = instance.getAttributeValue(Io.ATTR_LABEL_LOC);

		Bounds bds = instance.getBounds();
		int x = bds.getX() + bds.getWidth() / 2;
		int y = bds.getY() + bds.getHeight() / 2;
		int halign = GraphicsUtil.H_CENTER;
		int valign = GraphicsUtil.V_CENTER_OVERALL;
		if (labelLoc == Direction.NORTH) {
			y = bds.getY() - 2;
			valign = GraphicsUtil.V_BOTTOM;
		} else if (labelLoc == Direction.SOUTH) {
			y = bds.getY() + bds.getHeight() + 2;
			valign = GraphicsUtil.V_TOP;
		} else if (labelLoc == Direction.EAST) {
			x = bds.getX() + bds.getWidth() + 2;
			halign = GraphicsUtil.H_LEFT;
		} else if (labelLoc == Direction.WEST) {
			x = bds.getX() - 2;
			halign = GraphicsUtil.H_RIGHT;
		}
		if (labelLoc == facing) {
			if (labelLoc == Direction.NORTH || labelLoc == Direction.SOUTH) {
				x += 15;
				halign = GraphicsUtil.H_LEFT;
			} else {
				y -= 10;
				valign = GraphicsUtil.V_BOTTOM;
			}
		}

		instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT, StdAttr.ATTR_LABEL_COLOR, x, y, halign, valign);
	}

	@Override
	protected void configureNewInstance(Instance instance) {
		Bounds b = instance.getBounds();
		updateports(instance);
		instance.addAttributeListener();
		instance.setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT, StdAttr.ATTR_LABEL_COLOR, b.getX() + b.getWidth() / 2,
				b.getY() - 3, GraphicsUtil.H_CENTER, GraphicsUtil.V_BOTTOM);
		computeTextField(instance);
	}

	@Override
	public Bounds getOffsetBounds(AttributeSet attrs) {
		Direction dir = attrs.getValue(StdAttr.FACING);
		if (dir == Direction.EAST || dir == Direction.WEST)
			return Bounds.create(-40, -20, 40, 40).rotate(Direction.EAST, dir, 0, 0);
		else
			return Bounds.create(-20, 0, 40, 40).rotate(Direction.NORTH, dir, 0, 0);

	}

	@Override
	protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
		if (attr == StdAttr.FACING) {
			instance.recomputeBounds();
			updateports(instance);
			computeTextField(instance);
		} else if (attr == VOLUME_WIDTH) {
			updateports(instance);
			computeTextField(instance);
		} else if (attr == FREQUENCY_MEASURE) {
			instance.fireInvalidated();
		}
	}

	@Override
	public void paintGhost(InstancePainter painter) {
		Bounds b = painter.getBounds();
		Graphics g = painter.getGraphics();
		g.setColor(Color.GRAY);
		g.drawOval(b.getX(), b.getY(), 40, 40);
	}

	@Override
	public void paintInstance(InstancePainter painter) {
		Graphics g = painter.getGraphics();
		Bounds b = painter.getBounds();
		int x = b.getX();
		int y = b.getY();
		byte height = (byte) b.getHeight();
		byte width = (byte) b.getWidth();
		g.setColor(Color.DARK_GRAY);
		g.fillOval(x, y, 40, 40);
		g.setColor(Color.GRAY);
		GraphicsUtil.switchToWidth(g, 1.5f);
		for (byte k = 8; k <= 16; k += 4) {
			g.drawOval(x + 20 - k, y + 20 - k, k * 2, k * 2);
		}
		GraphicsUtil.switchToWidth(g, 2);
		g.setColor(Color.DARK_GRAY);
		g.drawLine(x + 4, y + height / 2, x + 36, y + height / 2);
		g.drawLine(x + width / 2, y + 4, x + width / 2, y + 36);
		g.setColor(Color.BLACK);
		g.fillOval(x + 15, y + 15, 10, 10);
		g.drawOval(x, y, 40, 40);
		painter.drawPorts();
		painter.drawLabel();
	}

	@Override
	public void propagate(InstanceState state) {
		Data d = (Data) state.getData();
		boolean active = state.getPort(ENABLE) == Value.TRUE;
		if (d == null) {
			state.setData(d = new Data());
		}
		d.is_on.set(active);

		int freq = state.getPort(FREQ).toIntValue();
		if (freq >= 0) {
			if (state.getAttributeValue(FREQUENCY_MEASURE) == dHz)
				freq /= 10;
			d.hz = freq;
		}
		if (state.getPort(VOL).isFullyDefined()) {
			int vol = state.getPort(VOL).toIntValue();
			byte VolumeWidth = (byte) state.getAttributeValue(VOLUME_WIDTH).getWidth();
			d.vol = (float) (((vol & 0xffffffffL) * 127) / (Math.pow(2, VolumeWidth) - 1));
		}
		if (active && !d.thread.isAlive())
			d.StartThread();
	}

	private void updateports(Instance instance) {
		Direction dir = instance.getAttributeValue(StdAttr.FACING);
		byte VolumeWidth = (byte) instance.getAttributeValue(VOLUME_WIDTH).getWidth();
		Port[] p = new Port[3];
		if (dir == Direction.EAST || dir == Direction.WEST) {
			p[FREQ] = new Port(0, -10, Port.INPUT, 12);
			p[VOL] = new Port(0, 10, Port.INPUT, VolumeWidth);
		} else {
			p[FREQ] = new Port(-10, 0, Port.INPUT, 12);
			p[VOL] = new Port(10, 0, Port.INPUT, VolumeWidth);
		}
		p[ENABLE] = new Port(0, 0, Port.INPUT, 1);
		p[FREQ].setToolTip(Strings.getter("buzzerFrequecy"));
		p[ENABLE].setToolTip(Strings.getter("enableSound"));
		p[VOL].setToolTip(Strings.getter("buzzerVolume"));
		instance.setPorts(p);
	}
}
