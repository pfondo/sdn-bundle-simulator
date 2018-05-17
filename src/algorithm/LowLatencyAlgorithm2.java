package algorithm;

import java.util.Set;

import auxiliar.PortNumber;
import auxiliar.Queue.QueueType;

/**
 * Low-latency algorithm version 2: Two-queues algorithm. Allocate low-latency
 * traffic as normal traffic, but using the high-priority queue for it.
 * 
 * @author pfondo
 *
 */

public class LowLatencyAlgorithm2 extends LowLatencyBaseAlgorithm {

	private static final boolean reallocateWithAlgorithm = true;

	private static final QueueType queueType = QueueType.HIGHPRIORITY;

	/**
	 * Selects the output port to allocate a new low-latency flow among the ports of
	 * the bundle.
	 * 
	 * @param ports
	 *            Set of ports of the bundle.
	 * @return Returns null to be treated as normal traffic.
	 */
	public PortNumber selectOutputPortLowLatency(Set<PortNumber> ports) {
		return null;
	}

	@Override
	public boolean mustReallocateWithAlgorithm() {
		return reallocateWithAlgorithm;
	}

	@Override
	public QueueType getQueueType() {
		return queueType;
	}

}
