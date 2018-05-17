package algorithm;

import java.util.Set;

import auxiliar.PortNumber;
import auxiliar.Queue.QueueType;

/**
 * Low-latency algorithm version 0: Do nothing with low-latency traffic. Treat
 * as normal traffic.
 * 
 * @author pfondo
 *
 */

public class LowLatencyAlgorithm0 extends LowLatencyBaseAlgorithm {

	private static final boolean reallocateWithAlgorithm = true;

	private static final QueueType queueType = QueueType.NORMAL;

	/**
	 * Selects the output port to allocate a new low-latency flow among the ports of
	 * the bundle. Treat as normal traffic.
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
