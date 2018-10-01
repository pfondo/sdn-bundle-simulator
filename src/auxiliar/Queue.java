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

	private long queueSize; // delay in nanoseconds

	private List<Packet> priorityList;
	private List<Packet> list;
	private long lastTransmittedTimestamp;
	private int numExceeded;
	private int bytesExceeded;
	private int maxPackets;

	private long accumulatedDelay;
	private long numPackets;

	private long accumulatedDelayLowLatency;
	private long numPacketsLowLatency;

	private long referenceTimestamp = 0;

	private BufferedWriter writer;
	private BufferedWriter lowLatencyIndexWriter;

	private String fileName;
	private String lowLatencyIndexFileName;

	// Accumulated delay of the packets in the queue
	long totalDelay = 0;

	// Accumulated delay of the packets in the queue
	long totalDelayPriority = 0;

	// Experimental
	private long packetCount;
	private long idleTime; // Time in seconds that the port has been idle

	private String portName;

	public Queue(String subFolder, String portName, long queueSize) {
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

	public void finishQueue(long currentTime) {
		// Updated final idle time
		if (isEmpty() && isEmptyPriority()) {
			long currentIdleTime;
			if (lastTransmittedTimestamp > 0) {
				currentIdleTime = Math
						.max(currentTime - (lastTransmittedTimestamp + ((long) (1e9 * EnergyConsumptionUtils.T_S))), 0);
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

	public void queueDiscardPrevious(long referenceTimestamp) {
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

	public void clean(long referenceTimestamp) {
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

	public long getAccumulatedDelay() {
		return accumulatedDelay;
	}

	public long getNumPackets() {
		return numPackets;
	}

	public void addPacket(Packet packet, boolean lowLatency, QueueType queueType) {
		if (DEBUG) {
			System.out.println(
					"addPacket(" + packet.getQueueArrivalTimestamp() + ", " + lowLatency + ", " + queueType + ")");
		}
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
				// Print to file of this port! (to be later processed by HystEEE simulator
				// (https://github.com/migrax/HystEEE))
				if (packet.getQueueArrivalTimestamp() - referenceTimestamp >= 0) {
					String toPrint = DecimalFormatUtils.getDecimalFormat9()
							.format((packet.getQueueArrivalTimestamp() - referenceTimestamp) / 1e9) + " "
							+ packet.getBytes() + "\n";
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
					String toPrint = DecimalFormatUtils.getDecimalFormat9()
							.format((packet.getQueueArrivalTimestamp() - referenceTimestamp) / 1e9) + " "
							+ packet.getBytes() + "\n";
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

	public void update(long currentTimestamp) {
		long transmittedPackets = -1;
		while (transmittedPackets != 0) {
			transmittedPackets = 0;
			transmittedPackets += updatePriority(currentTimestamp);
			transmittedPackets += updateNonPriority(currentTimestamp);
		}
	}

	public long updatePriority(long currentTimestamp) {
		long transmittedPackets = 0;
		while (!isEmptyPriority()) {
			long lastTransmittedTimestampTmp = lastTransmittedTimestamp;
			long currentIdleTime = 0;
			long portWakeUpTimestamp = priorityList.get(0).getQueueArrivalTimestamp();

			if (priorityList.get(0).getQueueArrivalTimestamp() > lastTransmittedTimestamp
					|| lastTransmittedTimestamp == 0) {
				long sleep_time_compensation = 0;
				if (lastTransmittedTimestamp > 0) {
					long timeSinceLastTransmissionEnded = portWakeUpTimestamp - lastTransmittedTimestamp;
					currentIdleTime = timeSinceLastTransmissionEnded - ((long) (1e9 * EnergyConsumptionUtils.T_S));
					if (currentIdleTime <= 0) {
						sleep_time_compensation = -currentIdleTime; // Note that this will be positive
					}

				} else {
					currentIdleTime = portWakeUpTimestamp - referenceTimestamp;
				}
				if (DEBUG) {
					System.out.println("sleep_time_compensation=" + sleep_time_compensation);
				}

				// Testing
				if (currentIdleTime > 0) {
					if (!isEmpty() && list.get(0).getQueueArrivalTimestamp() < priorityList.get(0)
							.getQueueArrivalTimestamp()) {
						portWakeUpTimestamp = list.get(0).getQueueArrivalTimestamp();
						// Evaluate if when the port is awake, the high priority packet is already in
						// the queue
						if (list.get(0).getQueueArrivalTimestamp()
								+ ((long) (1e9 * EnergyConsumptionUtils.T_W)) < priorityList.get(0)
										.getQueueArrivalTimestamp()) {
							// Then the low-priority packet will be transmitted before this high-priority
							// packet
							break;
						}
						if (lastTransmittedTimestamp > 0) {

							long timeSinceLastTransmissionEnded = portWakeUpTimestamp - lastTransmittedTimestamp;
							currentIdleTime = timeSinceLastTransmissionEnded
									- ((long) (1e9 * EnergyConsumptionUtils.T_S));
							if (currentIdleTime <= 0) {
								sleep_time_compensation = -currentIdleTime; // Note that this will be positive
							}
						} else {
							currentIdleTime = portWakeUpTimestamp - referenceTimestamp;
						}
					}
				}

				lastTransmittedTimestampTmp = sleep_time_compensation + portWakeUpTimestamp
						+ ((long) (1e9 * EnergyConsumptionUtils.T_W));
			}
			// From this point on, lastTransmittedTimestampTmp contains the timestamp when
			// the first packet in the queue will be transmitted
			if (currentTimestamp - lastTransmittedTimestampTmp > 0) { // TODO: Decide if >= or >.
				// The first packet in the queue can be transmitted
				Packet currentPacket = priorityList.remove(0);
				totalDelayPriority -= currentPacket.getTransmissionTime();

				// Update idleTime:
				if (currentIdleTime > 0) {
					// if (portName.equals("port4")) {
					// System.out.println("***");
					// System.out.println("portName=" + portName);
					// System.out.println("currentTimestamp=" + currentTimestamp);
					// System.out.println("lastTransmittedTimestampTmp=" +
					// lastTransmittedTimestampTmp);
					// System.out.println("lastTransmittedTimestamp=" + lastTransmittedTimestamp);
					// System.out.println("priorityListTs=" +
					// currentPacket.getQueueArrivalTimestamp());
					// System.out.println("txTime: " + currentPacket.getTransmissionTime());
					// if (!isEmpty()) {
					// System.out.println("listTs=" + list.get(0).getQueueArrivalTimestamp());
					// }
					// System.out.println("currentIdleTime= " + currentIdleTime);
					// }
					setIdleTime(getIdleTime() + currentIdleTime);
				}

				long packetDelay = currentPacket.getTransmissionTime() + lastTransmittedTimestampTmp
						- currentPacket.getQueueArrivalTimestamp();

				// System.out.println("***");
				// System.out.println("currentTimestamp=" + currentTimestamp);
				// System.out.println("lastTransmittedTimestampTmp=" +
				// lastTransmittedTimestampTmp);
				// System.out.println("lastTransmittedTimestamp=" + lastTransmittedTimestamp);
				// System.out.println("priorityListTs=" +
				// currentPacket.getQueueArrivalTimestamp());
				// System.out.println("txTime: " + currentPacket.getTransmissionTime());
				// if (!isEmpty()) {
				// System.out.println("listTs=" + list.get(0).getQueueArrivalTimestamp());
				// }

				// System.out.println(currentPacket.getQueueArrivalTimestamp() + " " +
				// packetDelay);

				if (currentPacket.isLowLatency()) {
					setAccumulatedDelayLowLatency(getAccumulatedDelayLowLatency() + packetDelay);
					setNumPacketsLowLatency(getNumPacketsLowLatency() + 1);
				} else {
					System.err.println(packetDelay);
					accumulatedDelay += packetDelay;
					numPackets += 1;
				}

				lastTransmittedTimestamp = lastTransmittedTimestampTmp + currentPacket.getTransmissionTime();
				transmittedPackets += 1;
			} else {
				break;
			}
		}
		return transmittedPackets;
	}

	public long updateNonPriority(long currentTimestamp) {
		long transmittedPackets = 0;
		while (!isEmpty()) {
			long lastTransmittedTimestampTmp = lastTransmittedTimestamp;
			long currentIdleTime = 0;
			if (list.get(0).getQueueArrivalTimestamp() > lastTransmittedTimestamp || lastTransmittedTimestamp == 0) {
				long sleep_time_compensation = 0;
				if (lastTransmittedTimestamp > 0) {
					long timeSinceLastTransmissionEnded = list.get(0).getQueueArrivalTimestamp()
							- lastTransmittedTimestamp;
					currentIdleTime = timeSinceLastTransmissionEnded - ((long) (1e9 * EnergyConsumptionUtils.T_S));
					if (currentIdleTime > 0) {
						// Only update when the packet is actually transmitted
					} else {
						sleep_time_compensation = -currentIdleTime;
					}

				} else {
					currentIdleTime = (list.get(0).getQueueArrivalTimestamp() - referenceTimestamp);
					// setIdleTime(getIdleTime() + currentIdleTime);
				}
				lastTransmittedTimestampTmp = sleep_time_compensation + list.get(0).getQueueArrivalTimestamp()
						+ ((long) (1e9 * EnergyConsumptionUtils.T_W));
			}
			// From this point on, lastTransmittedTimestampTmp contains the timestamp when
			// the first packet in the queue will be transmitted
			if (!isEmptyPriority() && priorityList.get(0).getQueueArrivalTimestamp() <= lastTransmittedTimestamp) {
				// Then a high-priority packet will be transmitted before this low-priority
				// packet
				break;
			}
			if (currentTimestamp - lastTransmittedTimestampTmp > 0) { // TODO: Decidir si >= ó >
				// The first packet in the queue can be transmitted
				Packet currentPacket = list.remove(0);
				totalDelay -= currentPacket.getTransmissionTime();

				// Update idleTime:
				if (currentIdleTime > 0) {
					setIdleTime(getIdleTime() + currentIdleTime);
				}

				long packetDelay = currentPacket.getTransmissionTime() + lastTransmittedTimestampTmp
						- currentPacket.getQueueArrivalTimestamp();

				// System.out.println(currentPacket.getQueueArrivalTimestamp() + " " +
				// packetDelay);

				if (currentPacket.isLowLatency()) {
					setAccumulatedDelayLowLatency(getAccumulatedDelayLowLatency() + packetDelay);
					setNumPacketsLowLatency(getNumPacketsLowLatency() + 1);
				} else {
					accumulatedDelay += packetDelay;
					numPackets += 1;
				}

				lastTransmittedTimestamp = lastTransmittedTimestampTmp + currentPacket.getTransmissionTime();
				transmittedPackets += 1;
			} else {
				break;
			}
		}
		return transmittedPackets;
	}

	public long getAccumulatedDelayLowLatency() {
		return accumulatedDelayLowLatency;
	}

	public void setAccumulatedDelayLowLatency(long accumulatedDelayLowLatency) {
		this.accumulatedDelayLowLatency = accumulatedDelayLowLatency;
	}

	public long getNumPacketsLowLatency() {
		return numPacketsLowLatency;
	}

	public void setNumPacketsLowLatency(long numPacketsLowLatency) {
		this.numPacketsLowLatency = numPacketsLowLatency;
	}

	public long getIdleTime() {
		return idleTime;
	}

	public void setIdleTime(long idleTime) {
		this.idleTime = idleTime;
	}

	public String getPortName() {
		return portName;
	}

	public void setPortName(String portName) {
		this.portName = portName;
	}

}
