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

/**
 * Version 2: Same as version 1 but maximum port occupation directly
 * proportional to the number of flows already allocated on the port.
 * 
 * @author pfondo
 *
 */
public class Algorithm2 extends BaseAlgorithm {

	@Override
	protected double getPortBytesAvailable(long numFlows) {
		final double C = 0.3;
		double portBytesAvailable = portBytesInterface;

		if (numFlows != 0) {
			portBytesAvailable *= (1 - C / numFlows);
		}
		return portBytesAvailable;
	}

	public Map<FlowEntry, PortNumber> computeAllocation(Map<FlowEntry, Long> flowMap, Set<PortNumber> linkPorts) {
		Map<FlowEntry, PortNumber> flowAllocation = new HashMap<FlowEntry, PortNumber>();
		/*
		 * Calculate statistics: Sort flows by byte difference from previous
		 * poll
		 */
		List<Entry<FlowEntry, Long>> flowList = new ArrayList<Entry<FlowEntry, Long>>(flowMap.entrySet());
		Collections.sort(flowList, new Comparator<Entry<FlowEntry, Long>>() {
			@Override
			public int compare(Entry<FlowEntry, Long> o1, Entry<FlowEntry, Long> o2) {
				return (int) Math.signum(o2.getValue() - o1.getValue());
			}
		});

		Map<PortNumber, PortStatistics> portOccupation = new HashMap<PortNumber, PortStatistics>();
		for (PortNumber pn : linkPorts) {
			portOccupation.put(pn, new PortStatistics(null, pn, delay, portBandwidth, 0));
		}

		List<Entry<PortNumber, PortStatistics>> sortedPortOccupation;

		// int count = 0;
		for (Entry<FlowEntry, Long> entryFlowEntry : flowList) {
			sortedPortOccupation = new ArrayList<Entry<PortNumber, PortStatistics>>(portOccupation.entrySet());
			Collections.sort(sortedPortOccupation, new Comparator<Entry<PortNumber, PortStatistics>>() {
				@Override
				public int compare(Entry<PortNumber, PortStatistics> o1, Entry<PortNumber, PortStatistics> o2) {
					return (int) Math.signum(o2.getValue().getBytes() - o1.getValue().getBytes());
				}
			});
			// To get the time flow has been active during the last interval

			long currentFlowBytes = entryFlowEntry.getValue();
			// Apply correction based on life of the current flow:
			currentFlowBytes *= delay
					/ Math.min(entryFlowEntry.getKey().life(networkSimulator.getCurrentTime()), delay);

			// System.out.println(count++ + "fe " +
			// entryFlowEntry.getKey().getId() + ": "
			// + entryFlowEntry.getKey().life(NetworkSimulator.getCurrentTime())
			// + "; "
			// + delay /
			// Math.min(entryFlowEntry.getKey().life(NetworkSimulator.getCurrentTime()),
			// delay));

			for (Entry<PortNumber, PortStatistics> entry : sortedPortOccupation) {
				PortNumber pn = entry.getKey();
				double portBytesAvailable = getPortBytesAvailable(entry.getValue().getNumFlowsInterval());
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
					// This flow only needs to be modified
					// if the port allocation is different
					// from the current
					flowAllocation.put(entryFlowEntry.getKey(), pn);
					break;
				}
			}
		}
		if (DEBUG) {
			for (PortNumber pn : portOccupation.keySet()) {
				System.out.println("_Port " + pn + ": " + portOccupation.get(pn) + " bytes");
			}
		}
		// System.out.println(flowAllocation);
		return flowAllocation;
	}

}
