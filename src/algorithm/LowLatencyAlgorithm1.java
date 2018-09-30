package algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import auxiliar.FlowEntry;
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

	@Override
	public Map<FlowEntry, PortNumber> computeAllocationLowLatency(BaseAlgorithm algorithm,
			Map<FlowEntry, Long> flowMapNormal, Map<FlowEntry, Long> flowMapLowLatency, Set<PortNumber> linkPorts) {
		long delay = algorithm.delay;
		double portBandwidth = algorithm.portBandwidth;
		Map<FlowEntry, PortNumber> flowAllocation = new HashMap<FlowEntry, PortNumber>();

		int neededPorts = linkPorts.size();

		List<Entry<FlowEntry, Long>> flowList = new ArrayList<Entry<FlowEntry, Long>>(flowMapLowLatency.entrySet());
		Collections.sort(flowList, new Comparator<Entry<FlowEntry, Long>>() {
			@Override
			public int compare(Entry<FlowEntry, Long> o1, Entry<FlowEntry, Long> o2) {
				return (int) Math.signum(o2.getValue() - o1.getValue());
			}
		});

		Map<PortNumber, PortStatistics> portOccupation = new HashMap<PortNumber, PortStatistics>();
		int count = 0;
		for (PortNumber pn : linkPorts) {
			if (count >= neededPorts) {
				break;
			}
			portOccupation.put(pn, new PortStatistics(null, pn, delay, portBandwidth, 0));
			count++;
		}

		// Initialize portOccupation based on nonLowLatencyFlows

		for (Entry<FlowEntry, Long> entryFlowEntry : flowMapNormal.entrySet()) {
			PortNumber pn = entryFlowEntry.getKey().getOutputPort();
			portOccupation.get(pn).addBytes(entryFlowEntry.getValue());
			portOccupation.get(pn).addFlow();
		}

		List<Entry<PortNumber, PortStatistics>> sortedPortOccupation;

		// Compute the expected load of the interface

		for (Entry<FlowEntry, Long> entryFlowEntry : flowList) {
			sortedPortOccupation = new ArrayList<Entry<PortNumber, PortStatistics>>(portOccupation.entrySet());
			Collections.sort(sortedPortOccupation, new Comparator<Entry<PortNumber, PortStatistics>>() {
				@Override
				public int compare(Entry<PortNumber, PortStatistics> o1, Entry<PortNumber, PortStatistics> o2) {
					// Minimize port occupation
					return (int) Math.signum(o1.getValue().getBytes() - o2.getValue().getBytes());
				}
			});
			// To get the time flow has been active during the last interval

			long currentFlowBytes = entryFlowEntry.getValue();
			// Apply correction based on life of the current flow:
			currentFlowBytes *= delay
					/ Math.min(entryFlowEntry.getKey().life(algorithm.networkSimulator.getCurrentTime()), delay);

			for (Entry<PortNumber, PortStatistics> entry : sortedPortOccupation) {
				PortNumber pn = entry.getKey();
				double portBytesAvailable = algorithm.getPortBytesAvailable(entry.getValue().getNumFlowsInterval());
				long total = portOccupation.get(pn).getBytes() + currentFlowBytes;
				// log.info("Port " + pn + ": " + total + "bytes");
				if (total <= portBytesAvailable || portOccupation.get(pn).getBytes() == 0) {
					// It can be allocated in this port
					// log.info("FE: " + flowEntry.getKey().id() + " -> " + pn);
					// System.out.println("+Port" + pn + ": " +
					// entryFlowEntry.getKey() + " -> "
					// + entryFlowEntry.getValue() + " bytes");
					portOccupation.get(pn).setBytes(total);
					portOccupation.get(pn).addFlow();
					// This flow only needs to be modified if the port
					// allocation is different from the current
					flowAllocation.put(entryFlowEntry.getKey(), pn);
					break;
				}
			}
		}

		// System.out.println(flowAllocation);
		return flowAllocation;
	}

}
