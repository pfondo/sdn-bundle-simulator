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
 * Version 6: Compute expected number of ports needed (port occupation +
 * security margin). Sequentially select the most empty port and then select
 * "best" flow to be allocated to it.
 * 
 * @author pfondo
 *
 */
public class Algorithm6 extends BaseAlgorithm {

	private static final double EXPECTED_LOAD_MARGIN = 0.2;

	private static final Comparator<Entry<FlowEntry, Long>> flowComparatorDecreasing = new Comparator<Entry<FlowEntry, Long>>() {
		@Override
		public int compare(Entry<FlowEntry, Long> o1, Entry<FlowEntry, Long> o2) {
			return (int) Math.signum(o2.getValue() - o1.getValue());
		}
	};

	private int lastNeededPorts = 0;

	private double computeExpectedLoad(Map<FlowEntry, Long> flowMap) {
		double expectedLoad = 0;
		for (long flowBytes : flowMap.values()) {
			expectedLoad += flowBytes;
		}
		expectedLoad /= portBytesInterface;
		return expectedLoad;
	}

	public Map<FlowEntry, PortNumber> computeAllocation(Map<FlowEntry, Long> flowMap, Set<PortNumber> linkPorts) {
		int numFlows = flowMap.size();
		Map<FlowEntry, PortNumber> flowAllocation = new HashMap<FlowEntry, PortNumber>();
		Map<PortNumber, List<Entry<FlowEntry, Long>>> currentAllocation = new HashMap<PortNumber, List<Entry<FlowEntry, Long>>>();

		double expectedLoad = computeExpectedLoad(flowMap);
		lastNeededPorts = (int) Math.ceil(expectedLoad + EXPECTED_LOAD_MARGIN);

		Map<PortNumber, PortStatistics> portOccupation = new HashMap<PortNumber, PortStatistics>();
		int count = 0;
		for (PortNumber pn : linkPorts) {
			if (count >= lastNeededPorts) {
				break;
			}
			portOccupation.put(pn, new PortStatistics(null, pn, delay, portBandwidth, 0));
			count++;
		}

		for (PortNumber pn : linkPorts) {
			currentAllocation.put(pn, new ArrayList<Entry<FlowEntry, Long>>());
		}

		// Compute current allocation
		for (Entry<FlowEntry, Long> entry : flowMap.entrySet()) {
			currentAllocation.get(entry.getKey().getOutputPort()).add(entry);
		}

		// Sort the list of flows of each port
		for (PortNumber pn : linkPorts) {
			Collections.sort(currentAllocation.get(pn), flowComparatorDecreasing);
		}

		// Determine the minimum number of flows that need to be rescheduled

		List<Entry<FlowEntry, Long>> flowsNeedReschedule = new ArrayList<Entry<FlowEntry, Long>>();

		// 1.- Flows belonging to ports currently inactive
		for (PortNumber pn : linkPorts) {
			if (!portOccupation.containsKey(pn)) {
				flowsNeedReschedule.addAll(currentAllocation.get(pn));
				// Remove the port from the currentAllocation
				currentAllocation.remove(pn);
			}
		}

		// Sort the list of flows that need to be rescheduled
		Collections.sort(flowsNeedReschedule, flowComparatorDecreasing);

		List<Entry<PortNumber, PortStatistics>> sortedPortOccupation;

		for (int i = 0; i < numFlows; i++) {
			sortedPortOccupation = new ArrayList<Entry<PortNumber, PortStatistics>>(portOccupation.entrySet());
			Entry<PortNumber, PortStatistics> entry = Collections.min(sortedPortOccupation,
					new Comparator<Entry<PortNumber, PortStatistics>>() {
						@Override
						public int compare(Entry<PortNumber, PortStatistics> o1, Entry<PortNumber, PortStatistics> o2) {
							// Minimize port occupation
							return (int) Math.signum(o1.getValue().getBytes() - o2.getValue().getBytes());
						}
					});

			// Current port being analyzed
			PortNumber pn = entry.getKey();

			// Select most adequate flow to the port
			Entry<FlowEntry, Long> entryFlowEntry = null;

			if (!currentAllocation.get(pn).isEmpty() && !flowsNeedReschedule.isEmpty()) {
				// TODO: Much higher, indeed (like more than double or something like: Actually, not very relevant)
				if (flowsNeedReschedule.get(0).getValue() > currentAllocation.get(pn).get(0).getValue()) {
					entryFlowEntry = flowsNeedReschedule.remove(0);
				} else {
					entryFlowEntry = currentAllocation.get(pn).remove(0);
				}
			} else if (currentAllocation.get(pn).isEmpty() && !flowsNeedReschedule.isEmpty()) {
				entryFlowEntry = flowsNeedReschedule.remove(0);
			} else if (!currentAllocation.get(pn).isEmpty() && flowsNeedReschedule.isEmpty()) {
				entryFlowEntry = currentAllocation.get(pn).remove(0);
			} else {
				for (PortNumber j : currentAllocation.keySet()) {
					if (!currentAllocation.get(j).isEmpty()) {
						if (entryFlowEntry == null
								|| currentAllocation.get(j).get(0).getValue() > entryFlowEntry.getValue()) {
							entryFlowEntry = currentAllocation.get(j).get(0);
						}
					}
				}
				// Remove the flow assigned from its list
				currentAllocation.get(entryFlowEntry.getKey().getOutputPort()).remove(0);
			}

			long currentFlowBytes = entryFlowEntry.getValue();
			// Apply correction based on life of the current flow:
			currentFlowBytes *= delay
					/ Math.min(entryFlowEntry.getKey().life(networkSimulator.getCurrentTime()), delay);

			long total = portOccupation.get(pn).getBytes() + currentFlowBytes;
			// log.info("Port " + pn + ": " + total + "bytes");
			// It can be allocated in this port
			// log.info("FE: " + flowEntry.getKey().id() + " -> " + pn);
			// System.out.println("+Port" + pn + ": " + entryFlowEntry.getKey() + " -> " +
			// entryFlowEntry.getValue() + " bytes");
			portOccupation.get(pn).setBytes(total);
			portOccupation.get(pn).addFlow();
			// This flow only needs to be modified if the port
			// allocation is different from the current
			flowAllocation.put(entryFlowEntry.getKey(), pn);
		}

		if (DEBUG)

		{
			// Add remaining ports to port occupation
			for (

			PortNumber pn : linkPorts) {
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
