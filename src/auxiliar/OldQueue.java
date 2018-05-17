package auxiliar;

import java.util.ArrayList;
import java.util.List;

/**
 * DEPRECATED: The queue (buffer) size is in number of packets!
 * 
 * @author pfondo
 *
 */
public class OldQueue {
	public final static boolean DEBUG = false;

	public int queueSize;

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

	public OldQueue(int queueSize) {
		list = new ArrayList<Packet>();
		lastTransmittedTimestamp = 0;
		numExceeded = 0;
		bytesExceeded = 0;
		maxPackets = 0;
		policy = POLICY_DROP;
		this.queueSize = queueSize;
	}

	public void cleanExceeded() {
		numExceeded = 0;
		bytesExceeded = 0;
	}

	public int getMaxPackets() {
		return maxPackets;
	}

	public boolean exceedThreshold() {
		return size() >= queueSize;
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
			if (list.get(0).isExpired(currentTimestamp - lastTransmittedTimestamp)) {
				if (DEBUG) {
					System.out.println("[DEBUG]     lastTxTimestamp=" + lastTransmittedTimestamp);
					System.out.println("[DEBUG]     list.size()=" + list.size());
					System.out.println("[DEBUG]     isExpired="
							+ list.get(0).isExpired(currentTimestamp - lastTransmittedTimestamp));
					System.out.println("[DEBUG]     transmissionTime=" + list.get(0).getTransmissionTime());
				}
				// The first packet in the queue can be transmitted
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
