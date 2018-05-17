package algorithm;

import java.util.Set;

import auxiliar.PortNumber;
import auxiliar.Queue.QueueType;

public abstract class LowLatencyBaseAlgorithm {

	public static LowLatencyBaseAlgorithm newInstance(Class<? extends LowLatencyBaseAlgorithm> algorithmType) {
		LowLatencyBaseAlgorithm instance = null;
		try {
			instance = algorithmType.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return instance;
	}

	/**
	 * Selects the output port to allocate a new low-latency flow among the ports of
	 * the bundle.
	 * 
	 * @param ports
	 *            Set of ports of the bundle.
	 * @return The port where this flow must be allocated. If it returns null, use
	 *         selectOutputPort() in BaseAlgorithm.
	 */
	public abstract PortNumber selectOutputPortLowLatency(Set<PortNumber> ports);

	public abstract boolean mustReallocateWithAlgorithm();

	public abstract QueueType getQueueType();

}
