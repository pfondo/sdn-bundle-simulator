package algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

import auxiliar.DeviceId;
import auxiliar.FlowEntry;
import auxiliar.PortNumber;

/**
 * Version 4: Compute expected number of ports needed (port occupation +
 * security margin). Sort the flows decreasingly on the expected load.
 * Reallocate a flow to the same port if the port has less allocated rate lower
 * than 1. Otherwise, allocate the flow to the most empty port.
 * 
 * @author pfondo
 *
 */
public class Algorithm4 extends BaseAlgorithm {

	private static final double EXPECTED_LOAD_MARGIN = 0.2;

	private int lastNeededPorts = 0;

	private double computeExpectedLoad(Map<FlowEntry, Long> flowMap) {
		double expectedLoad = 0;
		for (long flowBytes : flowMap.values()) {
			expectedLoad += flowBytes;
		}
		expectedLoad /= portBytesInterface;
		expectedLoad += EXPECTED_LOAD_MARGIN;
		return expectedLoad;
	}

	public Map<FlowEntry, PortNumber> computeAllocation(Map<FlowEntry, Long> flowMap, Set<PortNumber> linkPorts) {
		Map<FlowEntry, PortNumber> flowAllocation = new HashMap<FlowEntry, PortNumber>();

		lastNeededPorts = (int) Math.ceil(computeExpectedLoad(flowMap));

		List<Entry<FlowEntry, Long>> flowList = new ArrayList<Entry<FlowEntry, Long>>(flowMap.entrySet());
		Collections.sort(flowList, new Comparator<Entry<FlowEntry, Long>>() {
			@Override
			public int compare(Entry<FlowEntry, Long> o1, Entry<FlowEntry, Long> o2) {
				return (int) Math.signum(o2.getValue() - o1.getValue());
			}
		});

		Map<PortNumber, PortStatistics> portOccupation = new HashMap<PortNumber, PortStatistics>();
		int count = 0;
		for (PortNumber pn : linkPorts) {
			if (count >= lastNeededPorts) {
				break;
			}
			portOccupation.put(pn, new PortStatistics(null, pn, delay, portBandwidth, 0));
			count++;
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
					/ Math.min(entryFlowEntry.getKey().life(networkSimulator.getCurrentTime()), delay);

			for (int i = 0; i < sortedPortOccupation.size(); i++) {
				Entry<PortNumber, PortStatistics> portEntry = sortedPortOccupation.get(i);
				// If the port where it is allocated will be active, set as first attempt
				if (portEntry.getKey().equals(entryFlowEntry.getKey().getOutputPort())) {
					portEntry = sortedPortOccupation.remove(i);
					sortedPortOccupation.add(0, portEntry);
					break;
				}
			}

			for (Entry<PortNumber, PortStatistics> entry : sortedPortOccupation) {
				PortNumber pn = entry.getKey();
				double portBytesAvailable = getPortBytesAvailable(entry.getValue().getNumFlowsInterval());
				long total = portOccupation.get(pn).getBytes() + currentFlowBytes;
				// log.info("Port " + pn + ": " + total + "bytes");
				if (total <= portBytesAvailable || portOccupation.get(pn).getBytes() == 0) {
					// It can be allocated in this port
					// log.info("FE: " + flowEntry.getKey().id() + " -> " + pn);
					// System.out.println("+Port" + pn + ": " + entryFlowEntry.getKey() + " -> " +
					// entryFlowEntry.getValue() + " bytes");
					portOccupation.get(pn).setBytes(total);
					portOccupation.get(pn).addFlow();
					// This flow only needs to be modified if the port
					// allocation is different from the current
					flowAllocation.put(entryFlowEntry.getKey(), pn);
					break;
				}
			}
		}

		if (DEBUG) {
			// Add remaining ports to port occupation
			for (PortNumber pn : linkPorts) {
				if (!portOccupation.containsKey(pn)) {
					portOccupation.put(pn, new PortStatistics(null, pn, delay, portBandwidth, 0));
				}
			}
			for (PortNumber pn : portOccupation.keySet()) {
				System.out.println("_Port " + pn + ": " + portOccupation.get(pn) + " bytes");
			}
		}
		// System.out.println(flowAllocation);
		return flowAllocation;
	}

	@Override
	public PortNumber selectOutputPort(DeviceId src, DeviceId dst) {
		PortNumber portNumber = null;
		if (dst != null) {
			Set<PortNumber> aggregation = new TreeSet<PortNumber>(new Comparator<PortNumber>() {

				@Override
				public int compare(PortNumber o1, PortNumber o2) {
					return (int) Math.signum(o1.toLong() - o2.toLong());
				}

			});
			int count = 0;
			for (PortNumber pn : getLinkPorts(src, dst)) {
				if (lastNeededPorts > 1 && count >= lastNeededPorts) {
					break;
				}
				aggregation.add(pn);
				count++;
			}

			int selected = (int) (aggregation.size() * random.nextDouble());
			portNumber = (PortNumber) aggregation.toArray()[selected];
		}
		return portNumber;
	}

}
