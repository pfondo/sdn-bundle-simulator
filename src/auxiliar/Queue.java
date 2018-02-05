package auxiliar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import utils.DecimalFormatUtils;

/**
 * The queue (buffer) size is in maximum delay (in seconds) allowed!
 * 
 * @author pfondo
 *
 */
public class Queue {
	public final static boolean DEBUG = false;
	public final static boolean PRINT_PACKETS = false;
	public final static String PACKETS_PATH = "packets/";

	public double queueSize; // delay in seconds

	private String portName;

	/**
	 * Drop packets that exceed the threshold.
	 */
	public final static int POLICY_DROP = 0;

	/**
	 * Never drop packets.
	 */
	public final static int POLICY_HOLD = 1;

	private List<Packet> list;
	private double lastTransmittedTimestamp;
	private int numExceeded;
	private int bytesExceeded;
	private int maxPackets;
	private int policy;

	private double accumulatedDelay;
	private long numPackets;

	private double referenceTimestamp = 0;

	public Queue(String portName, double queueSize) {
		this.list = new ArrayList<Packet>();
		this.lastTransmittedTimestamp = 0;
		this.numExceeded = 0;
		this.bytesExceeded = 0;
		this.accumulatedDelay = 0;
		this.numPackets = 0;
		this.maxPackets = 0;
		this.policy = POLICY_DROP;
		this.portName = portName;
		this.queueSize = queueSize;

		// Ensure the folder exists and it is empty
		File directory = new File(PACKETS_PATH);
		if (!directory.exists()) {
			directory.mkdir();
		} else {
			cleanPacketsDirectory();
		}

	}

	public void cleanPacketsDirectory() {
		// Ensure the folder exists and it is empty
		File directory = new File(PACKETS_PATH);
		if (!directory.exists()) {
			directory.mkdir();
		} else {
			File[] files = directory.listFiles();
			if (files != null) {
				for (File f : files) {
					if (!f.isDirectory()) {
						f.delete();
					}
				}
			}
		}
	}

	public void clean() {
		cleanExceeded();
		cleanDelay();
		cleanPacketsDirectory();
	}

	public void cleanExceeded() {
		numExceeded = 0;
		bytesExceeded = 0;
	}

	public void cleanDelay() {
		accumulatedDelay = 0;
		numPackets = 0;
	}

	public int getMaxPackets() {
		return maxPackets;
	}

	public boolean exceedThreshold() {
		double totalDelay = 0;
		for (Packet packet : list) {
			totalDelay += packet.getTransmissionTime();
		}
		return totalDelay >= queueSize;
	}

	public int getNumExceeded() {
		return numExceeded;
	}

	public int getBytesExceeded() {
		return bytesExceeded;
	}

	public int size() {
		return list.size();
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}

	public double getAccumulatedDelay() {
		return accumulatedDelay;
	}

	public long getNumPackets() {
		return numPackets;
	}

	public void addPacket(Packet packet) {
		if (DEBUG) {
			System.out.println("[DEBUG] Added packet to queue: currentTimestamp=" + packet.getQueueArrivalTimestamp()
					+ ", transmissionTime=" + packet.getTransmissionTime());
		}

		if (exceedThreshold()) {
			numExceeded += 1;
			bytesExceeded += packet.getBytes();
			if (policy == POLICY_HOLD) {
				list.add(packet);
			}
		} else {
			list.add(packet);
		}
		maxPackets = Math.max(maxPackets, list.size());
	}

	public void update(double currentTimestamp) {
		while (!isEmpty()) {
			if (list.get(0).getQueueArrivalTimestamp() > lastTransmittedTimestamp) {
				lastTransmittedTimestamp = list.get(0).getQueueArrivalTimestamp();
			}
			// From this point on, lastTransmittedTimestamp contains the timestamp when
			// the first packet in the queue will be transmitted
			if (list.get(0).isExpired(currentTimestamp - lastTransmittedTimestamp)) {
				// The first packet in the queue can be transmitted
				if (DEBUG) {
					System.out.println("[DEBUG]     lastTxTimestamp=" + lastTransmittedTimestamp);
					System.out.println("[DEBUG]     list.size()=" + list.size());
					System.out.println("[DEBUG]     isExpired="
							+ list.get(0).isExpired(currentTimestamp - lastTransmittedTimestamp));
					System.out.println("[DEBUG]     transmissionTime=" + list.get(0).getTransmissionTime());
				}
				if (PRINT_PACKETS) {
					// Print to file of this port! (Miguel EEE simulator)
					String toPrint = DecimalFormatUtils.getDecimalFormat9()
							.format(list.get(0).getQueueArrivalTimestamp() - referenceTimestamp) + " "
							+ list.get(0).getBytes() + "\n";
					synchronized (this) {
						try {
							Files.write(Paths.get(PACKETS_PATH + portName), toPrint.getBytes(),
									StandardOpenOption.APPEND);
						} catch (IOException e) {
							try {
								referenceTimestamp = list.get(0).getQueueArrivalTimestamp();
								toPrint = DecimalFormatUtils.getDecimalFormat9()
										.format(list.get(0).getQueueArrivalTimestamp() - referenceTimestamp) + " "
										+ list.get(0).getBytes() + "\n";
								Files.write(Paths.get(PACKETS_PATH + portName), toPrint.getBytes(),
										StandardOpenOption.CREATE_NEW);
							} catch (IOException e2) {
								System.err.println("Error writing file: " + PACKETS_PATH + portName);
							}
						}
					}
				}

				double packetDelay = lastTransmittedTimestamp - list.get(0).getQueueArrivalTimestamp();
				accumulatedDelay += packetDelay;
				numPackets += 1;

				lastTransmittedTimestamp = lastTransmittedTimestamp + list.remove(0).getTransmissionTime();
			} else {
				break;
			}
		}
		if (!list.isEmpty()) {
			if (DEBUG) {
				System.out.println("[DEBUG] Unable to transmit packet: currentTimestamp=" + currentTimestamp
						+ ", lastTxTimestamp=" + lastTransmittedTimestamp + ", list.size()=" + list.size()
						+ ", transmissionTime=" + list.get(0).getTransmissionTime());
			}
		}
	}

}
