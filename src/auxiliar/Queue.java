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
 * The queue (buffer) size is in maximum delay (in seconds) allowed. When the
 * queue is full, the packets that arrive to queue are discarded.
 * 
 * @author pfondo
 *
 */
public class Queue {
	public enum QueueType {
		NORMAL, HIGHPRIORITY
	}

	public static final boolean DEBUG = false;
	private static final boolean PRINT_PACKETS = false;

	private double queueSize; // delay in seconds

	private List<Packet> priorityList;
	private List<Packet> list;
	private double lastTransmittedTimestamp;
	private int numExceeded;
	private int bytesExceeded;
	private int maxPackets;

	private double accumulatedDelay;
	private long numPackets;

	private double accumulatedDelayLowLatency;
	private long numPacketsLowLatency;

	private double referenceTimestamp = 0;

	private BufferedWriter writer;
	private BufferedWriter lowLatencyIndexWriter;

	private String fileName;
	private String lowLatencyIndexFileName;

	// Accumulated delay of the packets in the queue
	double totalDelay = 0;

	// Accumulated delay of the packets in the queue
	double totalDelayPriority = 0;

	// Experimental
	private long packetCount;
	private double idleTime; // Time in seconds that the port has been idle

	private String portName;

	public Queue(String subFolder, String portName, double queueSize) {
		this.list = new ArrayList<Packet>();
		this.priorityList = new ArrayList<Packet>();
		this.lastTransmittedTimestamp = 0;
		this.numExceeded = 0;
		this.bytesExceeded = 0;
		this.accumulatedDelay = 0;
		this.setAccumulatedDelayLowLatency(0);
		this.setNumPacketsLowLatency(0);
		this.numPackets = 0;
		this.maxPackets = 0;
		this.queueSize = queueSize;
		this.packetCount = 0;
		this.totalDelay = 0;
		this.totalDelayPriority = 0;
		this.setIdleTime(0);
		this.setPortName(portName);

		if (PRINT_PACKETS) {
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

	public void finishQueue(double currentTime) {
		// Updated final idle time
		if (isEmpty()) {
			double currentIdleTime;
			if (lastTransmittedTimestamp > 0) {
				currentIdleTime = Math.max(currentTime - (lastTransmittedTimestamp + EnergyConsumptionUtils.T_S), 0);
			} else {
				currentIdleTime = Math.max(currentTime - referenceTimestamp, 0);
			}
			setIdleTime(getIdleTime() + currentIdleTime);
		}
		if (PRINT_PACKETS) {
			closeWriters();
		}
	}

	private void closeWriters() {
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
		if (PRINT_PACKETS) {
			closeWriters();
			removeQueueFile();
			removeLowLatencyIndexFile();
			initWriters();
		}
		cleanIdleTime();
		emptyQueue();
		this.referenceTimestamp = referenceTimestamp;
	}

	public void clean(double referenceTimestamp) {
		cleanExceeded();
		cleanDelay();
		cleanPacketCount();
		queueDiscardPrevious(referenceTimestamp);
	}

	public void emptyQueue() {
		lastTransmittedTimestamp = 0;
		totalDelay = 0;
		totalDelayPriority = 0;
		list.clear();
		priorityList.clear();
	}

	public void cleanIdleTime() {
		setIdleTime(0);
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
		return totalDelay >= queueSize;
	}

	public boolean exceedThresholdPriority() {
		return totalDelayPriority >= queueSize;
	}

	public int getNumExceeded() {
		return numExceeded;
	}

	public int getBytesExceeded() {
		return bytesExceeded;
	}

	public boolean isEmptyPriority() {
		return priorityList.isEmpty();
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

	public void addPacket(Packet packet, boolean lowLatency, QueueType queueType) {
		if (lowLatency && queueType.equals(QueueType.HIGHPRIORITY)) {
			addPriorityPacket(packet);
		} else {
			addNonPriorityPacket(packet);
		}
	}

	public void addNonPriorityPacket(Packet packet) {
		if (DEBUG) {
			System.out.println("[DEBUG] Added packet to queue: currentTimestamp=" + packet.getQueueArrivalTimestamp()
					+ ", transmissionTime=" + packet.getTransmissionTime());
		}

		if (exceedThreshold()) {
			numExceeded += 1;
			bytesExceeded += packet.getBytes();
		} else {
			list.add(packet);
			totalDelay += packet.getTransmissionTime();
			if (PRINT_PACKETS) {
				// Print to file of this port! (to be later processed by Miguel's HystEEE
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

	public void addPriorityPacket(Packet packet) {
		if (DEBUG) {
			System.out.println("[DEBUG] Added priority packet to queue of port " + portName + ": currentTimestamp="
					+ packet.getQueueArrivalTimestamp() + ", transmissionTime=" + packet.getTransmissionTime());
		}

		if (exceedThresholdPriority()) {
			numExceeded += 1;
			bytesExceeded += packet.getBytes();
		} else {
			priorityList.add(packet);
			totalDelayPriority += packet.getTransmissionTime();
			if (PRINT_PACKETS) {
				// Print to file of this port! (to be later processed by Miguel's HystEEE
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
		maxPackets = Math.max(maxPackets, priorityList.size());
	}

	public void update(double currentTimestamp) {
		if (!isEmptyPriority()) {
			updatePriority(currentTimestamp);
		}
		if (isEmptyPriority()) {
			updateNonPriority(currentTimestamp);
		}
	}

	public void updatePriority(double currentTimestamp) {
		while (!isEmptyPriority()) {
			double lastTransmittedTimestampTmp = lastTransmittedTimestamp;
			double currentIdleTime = 0;
			if (priorityList.get(0).getQueueArrivalTimestamp() > lastTransmittedTimestamp
					|| lastTransmittedTimestamp == 0) {
				double sleep_time_compensation = 0;
				if (lastTransmittedTimestamp > 0) {
					double timeSinceLastTransmissionEnded = priorityList.get(0).getQueueArrivalTimestamp()
							- lastTransmittedTimestamp;
					currentIdleTime = timeSinceLastTransmissionEnded - EnergyConsumptionUtils.T_S;
					if (currentIdleTime > 0) {
						// setIdleTime(getIdleTime() + currentIdleTime);
					} else {
						sleep_time_compensation = -currentIdleTime; // Note that this will be positive
					}

				} else {
					currentIdleTime = (priorityList.get(0).getQueueArrivalTimestamp() - referenceTimestamp);
				}
				if (DEBUG) {
					System.out.println("sleep_time_compensation=" + sleep_time_compensation);
				}
				lastTransmittedTimestampTmp = sleep_time_compensation + priorityList.get(0).getQueueArrivalTimestamp()
						+ EnergyConsumptionUtils.T_W;
			}
			// From this point on, lastTransmittedTimestamp contains the timestamp when the
			// first packet in the queue will be transmitted
			if (currentTimestamp - lastTransmittedTimestampTmp >= 0) { // TODO: Decide if >= or >
				// The first packet in the queue can be transmitted
				Packet currentPacket = priorityList.remove(0);
				totalDelayPriority -= currentPacket.getTransmissionTime();

				// Update idleTime:
				if (currentIdleTime > 0) {
					setIdleTime(getIdleTime() + currentIdleTime);
				}

				double packetDelay = currentPacket.getTransmissionTime() + lastTransmittedTimestampTmp
						- currentPacket.getQueueArrivalTimestamp();

				if (DEBUG) {
					System.out.println("[DEBUG] Added priority packet to queue of port " + portName
							+ ": queueArrivalTimestamp=" + currentPacket.getQueueArrivalTimestamp()
							+ ", transmissionTime=" + currentPacket.getTransmissionTime()
							+ ", lastTransmittedTimestamp=" + lastTransmittedTimestampTmp + ", delay=" + packetDelay);
				}
				if (currentPacket.isLowLatency()) {
					setAccumulatedDelayLowLatency(getAccumulatedDelayLowLatency() + packetDelay);
					setNumPacketsLowLatency(getNumPacketsLowLatency() + 1);
				} else {
					accumulatedDelay += packetDelay;
					numPackets += 1;
					// System.err.println("[JAVA] " + fileName.split("/")[fileName.split("/").length
					// - 1] + " "
					// + DecimalFormatUtils.getDecimalFormat4().format(packetDelay * 1e6));
				}

				lastTransmittedTimestamp = lastTransmittedTimestampTmp + currentPacket.getTransmissionTime();
			} else {
				break;
			}
		}
	}

	public void updateNonPriority(double currentTimestamp) {
		while (!isEmpty()) {
			double lastTransmittedTimestampTmp = lastTransmittedTimestamp;
			double currentIdleTime = 0;
			if (list.get(0).getQueueArrivalTimestamp() > lastTransmittedTimestamp || lastTransmittedTimestamp == 0) {
				double sleep_time_compensation = 0;
				if (lastTransmittedTimestamp > 0) {
					double timeSinceLastTransmissionEnded = list.get(0).getQueueArrivalTimestamp()
							- lastTransmittedTimestamp;
					currentIdleTime = timeSinceLastTransmissionEnded - EnergyConsumptionUtils.T_S;
					if (currentIdleTime > 0) {
						// setIdleTime(getIdleTime() + currentIdleTime);
					} else {
						sleep_time_compensation = -currentIdleTime;
					}

				} else {
					currentIdleTime = (list.get(0).getQueueArrivalTimestamp() - referenceTimestamp);
					// setIdleTime(getIdleTime() + currentIdleTime);
				}
				lastTransmittedTimestampTmp = sleep_time_compensation + list.get(0).getQueueArrivalTimestamp()
						+ EnergyConsumptionUtils.T_W;
			}
			// From this point on, lastTransmittedTimestamp contains the timestamp when
			// the first packet in the queue will be transmitted
			if (currentTimestamp - lastTransmittedTimestampTmp >= 0) { // TODO: Decidir si >= ó >
				// The first packet in the queue can be transmitted
				Packet currentPacket = list.remove(0);
				totalDelay -= currentPacket.getTransmissionTime();

				// Update idleTime:
				if (currentIdleTime > 0) {
					setIdleTime(getIdleTime() + currentIdleTime);
				}

				// Experimental: Recently included transmission time!
				double packetDelay = currentPacket.getTransmissionTime() + lastTransmittedTimestampTmp
						- currentPacket.getQueueArrivalTimestamp();

				if (currentPacket.isLowLatency()) {
					setAccumulatedDelayLowLatency(getAccumulatedDelayLowLatency() + packetDelay);
					setNumPacketsLowLatency(getNumPacketsLowLatency() + 1);
				} else {
					accumulatedDelay += packetDelay;
					numPackets += 1;
					// System.err.println("[JAVA] " + fileName.split("/")[fileName.split("/").length
					// - 1] + " "
					// + DecimalFormatUtils.getDecimalFormat4().format(packetDelay * 1e6));
				}

				lastTransmittedTimestamp = lastTransmittedTimestampTmp + currentPacket.getTransmissionTime();
			} else {
				break;
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

	public double getIdleTime() {
		return idleTime;
	}

	public void setIdleTime(double idleTime) {
		this.idleTime = idleTime;
	}

	public String getPortName() {
		return portName;
	}

	public void setPortName(String portName) {
		this.portName = portName;
	}

}
