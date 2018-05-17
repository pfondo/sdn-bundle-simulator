package algorithm;

import java.util.Set;

import auxiliar.PortNumber;
import auxiliar.Queue.QueueType;

/**
 * Low-latency algorithm version 1: Empty-port algorithm. Allocate low-latency
 * traffic to the most empty port.
 * 
 * @author pfondo
 *
 */

public class LowLatencyAlgorithm1 extends LowLatencyBaseAlgorithm {

	private static final boolean reallocateWithAlgorithm = false;

	private static final QueueType queueType = QueueType.NORMAL;

	/**
	 * Selects the output port to allocate a new low-latency flow among the ports of
	 * the bundle.
	 * 
	 * @param ports
	 *            Set of ports of the bundle.
	 * @return Returns the highest port.
	 */
	public PortNumber selectOutputPortLowLatency(Set<PortNumber> ports) {
		PortNumber portNumber = null;
		if (ports != null) {
			long maxPortNumber = 0;
			for (PortNumber pn : ports) {
				if (pn.toLong() > maxPortNumber) {
					maxPortNumber = pn.toLong();
					portNumber = pn;
				}
			}
		}
		return portNumber;
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
