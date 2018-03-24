package auxiliar;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tfm.EnergyConsumptionUtils;
import utils.DecimalFormatUtils;
import utils.FileNameUtils;

/**
 * The queue (buffer) size is in maximum delay (in seconds) allowed!
 * 
 * @author pfondo
 *
 */
public class Queue {
	public final static boolean DEBUG = false;
	public final static boolean PRINT_PACKETS = true;

	public double queueSize; // delay in seconds

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

	private double accumulatedDelayLowLatency;
	private long numPacketsLowLatency;

	private double referenceTimestamp = 0;

	private BufferedWriter writer;
	private BufferedWriter lowLatencyIndexWriter;

	private String fileName;
	private String lowLatencyIndexFileName;

	// Experimental
	private long packetCount;

	public Queue(String subFolder, String portName, double queueSize) {
		this.list = new ArrayList<Packet>();
		this.lastTransmittedTimestamp = 0;
		this.numExceeded = 0;
		this.bytesExceeded = 0;
		this.accumulatedDelay = 0;
		this.setAccumulatedDelayLowLatency(0);
		this.setNumPacketsLowLatency(0);
		this.numPackets = 0;
		this.maxPackets = 0;
		this.policy = POLICY_DROP;
		this.queueSize = queueSize;
		this.packetCount = 0;
		// Ensure the folder exists and it is empty
		File directory = new File(FileNameUtils.PACKETS_PATH + subFolder);
		fileName = FileNameUtils.PACKETS_PATH + subFolder + FileNameUtils.FOLDER_SEPARATOR + portName;
		lowLatencyIndexFileName = FileNameUtils.PACKETS_PATH + subFolder + FileNameUtils.FOLDER_SEPARATOR + portName
				+ FileNameUtils.LOW_LATENCY_INDEX_FILE_SUFIX;
		if (!directory.exists()) {
			directory.mkdirs();
		} else {
			removeQueueFile();
			removeLowLatencyIndexFile();
		}
		initWriters();
	}

	public void emptyQueue() {
		lastTransmittedTimestamp = 0;
		list.clear();
	}

	private void initWriters() {
		try {
			writer = new BufferedWriter(new FileWriter(fileName));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			lowLatencyIndexWriter = new BufferedWriter(new FileWriter(lowLatencyIndexFileName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void finishQueue() {
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			lowLatencyIndexWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void removeQueueFile() {
		File file = new File(fileName);
		if (file.exists()) {
			file.delete();
		}
	}

	private void removeLowLatencyIndexFile() {
		File file = new File(lowLatencyIndexFileName);
		if (file.exists()) {
			file.delete();
		}
	}

	public void queueDiscardPrevious(double referenceTimestamp) {
		finishQueue();
		removeQueueFile();
		removeLowLatencyIndexFile();
		initWriters();
		emptyQueue();
		this.referenceTimestamp = referenceTimestamp;
	}

	public void clean(double referenceTimestamp) {
		cleanExceeded();
		cleanDelay();
		cleanPacketCount();
		queueDiscardPrevious(referenceTimestamp);
	}

	public void cleanPacketCount() {
		packetCount = 0;
	}

	public void cleanExceeded() {
		numExceeded = 0;
		bytesExceeded = 0;
	}

	public void cleanDelay() {
		accumulatedDelay = 0;
		numPackets = 0;
		setAccumulatedDelayLowLatency(0);
		setNumPacketsLowLatency(0);
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
			if (PRINT_PACKETS) {
				// Print to file of this port! (to be later processed by Miguel HystEEE
				// simulator (https://github.com/migrax/HystEEE))
				if (packet.getQueueArrivalTimestamp() - referenceTimestamp >= 0) {
					String toPrint = DecimalFormatUtils.getDecimalFormat9().format(
							packet.getQueueArrivalTimestamp() - referenceTimestamp) + " " + packet.getBytes() + "\n";
					try {
						writer.write(toPrint);
						if (packet.isLowLatency()) {
							lowLatencyIndexWriter.write(packetCount + "\n");
						}
						packetCount += 1;
					} catch (IOException e) {
						e.printStackTrace();
						System.out.println(writer);
						System.exit(1);
					}
				}
			}
		}
		maxPackets = Math.max(maxPackets, list.size());
	}

	public void update(double currentTimestamp) {
		while (!isEmpty()) {
			if (list.get(0).getQueueArrivalTimestamp() > lastTransmittedTimestamp || lastTransmittedTimestamp == 0) {
				// TODO: Working here! Experimental T_W
				double sleep_time_compensation = 0;
				if (lastTransmittedTimestamp > 0) {
					double timeSinceLastTransmissionEnded = list.get(0).getQueueArrivalTimestamp()
							- lastTransmittedTimestamp;
					sleep_time_compensation = Math.max(EnergyConsumptionUtils.T_S - timeSinceLastTransmissionEnded, 0);
				}
				lastTransmittedTimestamp = sleep_time_compensation + list.get(0).getQueueArrivalTimestamp()
						+ EnergyConsumptionUtils.T_W;
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

				// Experimental: Recently included transmission time!
				double packetDelay = list.get(0).getTransmissionTime() + lastTransmittedTimestamp
						- list.get(0).getQueueArrivalTimestamp();
				if (list.get(0).isLowLatency()) {
					setAccumulatedDelayLowLatency(getAccumulatedDelayLowLatency() + packetDelay);
					setNumPacketsLowLatency(getNumPacketsLowLatency() + 1);
				} else {
					accumulatedDelay += packetDelay;
					numPackets += 1;
					// System.err.println("[JAVA] " + fileName.split("/")[fileName.split("/").length
					// - 1] + " "
					// + DecimalFormatUtils.getDecimalFormat4().format(packetDelay * 1e6));
				}

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

	public double getAccumulatedDelayLowLatency() {
		return accumulatedDelayLowLatency;
	}

	public void setAccumulatedDelayLowLatency(double accumulatedDelayLowLatency) {
		this.accumulatedDelayLowLatency = accumulatedDelayLowLatency;
	}

	public long getNumPacketsLowLatency() {
		return numPacketsLowLatency;
	}

	public void setNumPacketsLowLatency(long numPacketsLowLatency) {
		this.numPacketsLowLatency = numPacketsLowLatency;
	}

}
