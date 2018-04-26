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
 * Version 8: Compute expected number of ports needed (port occupation +
 * security margin). If the expected number of ports needed is different from
 * the lastNeededPorts, then compute allocation as per Algorithm3. Otherwise,
 * compute allocation according to Algorithm7.
 * 
 * @author pfondo
 *
 */
public class Algorithm8 extends BaseAlgorithm {

	private int lastNeededPorts = 0;

	private static final double EXPECTED_LOAD_MARGIN = 0.2;

	// Does not take into account flows that must be rescheduled (ports currently
	// inactive)
	private static final double MAX_FLOW_MODS = 20;

	private static final Comparator<Entry<FlowEntry, Long>> flowComparatorDecreasing = new Comparator<Entry<FlowEntry, Long>>() {
		@Override
		public int compare(Entry<FlowEntry, Long> o1, Entry<FlowEntry, Long> o2) {
			return (int) Math.signum(o2.getValue() - o1.getValue());
		}
	};

	private static final Comparator<Entry<PortNumber, PortStatistics>> portComparatorIncreasing = new Comparator<Entry<PortNumber, PortStatistics>>() {
		@Override
		public int compare(Entry<PortNumber, PortStatistics> o1, Entry<PortNumber, PortStatistics> o2) {
			// Minimize port occupation
			return (int) Math.signum(o1.getValue().getBytes() - o2.getValue().getBytes());
		}
	};

	private double computeExpectedLoad(Map<FlowEntry, Long> flowMap) {
		double expectedLoad = 0;
		for (long flowBytes : flowMap.values()) {
			expectedLoad += flowBytes;
		}
		expectedLoad /= portBytesInterface;
		return expectedLoad;
	}

	public Map<FlowEntry, PortNumber> computeAllocation(Map<FlowEntry, Long> flowMap, Set<PortNumber> linkPorts) {

		double expectedLoad = computeExpectedLoad(flowMap);
		double currentNeededPorts = (int) Math.ceil(expectedLoad + EXPECTED_LOAD_MARGIN);
		if (currentNeededPorts != lastNeededPorts) {
			return computeAllocationAlg3(flowMap, linkPorts);
		} else {
			return computeAllocationAlg7(flowMap, linkPorts);
		}

	}

	public Map<FlowEntry, PortNumber> computeAllocationAlg7(Map<FlowEntry, Long> flowMap, Set<PortNumber> linkPorts) {
		double expectedLoad = computeExpectedLoad(flowMap);
		lastNeededPorts = (int) Math.ceil(expectedLoad + EXPECTED_LOAD_MARGIN);

		Map<FlowEntry, PortNumber> flowAllocation = new HashMap<FlowEntry, PortNumber>();
		Map<PortNumber, List<Entry<FlowEntry, Long>>> currentAllocation = new HashMap<PortNumber, List<Entry<FlowEntry, Long>>>();

		for (PortNumber pn : linkPorts) {
			currentAllocation.put(pn, new ArrayList<Entry<FlowEntry, Long>>());
		}

		// Compute current allocation
		for (Entry<FlowEntry, Long> entry : flowMap.entrySet()) {
			// TODO: This should be done before calling this method (in BaseAlgorithm)
			entry.setValue((long) (entry.getValue() * delay
					/ Math.min(entry.getKey().life(networkSimulator.getCurrentTime()), delay)));
			currentAllocation.get(entry.getKey().getOutputPort()).add(entry);
		}

		reallocateFlows(currentAllocation, linkPorts, expectedLoad, lastNeededPorts, getPortBytesAvailable(0));

		// Dump new allocation to flowAllocation result map (format conversion)
		for (PortNumber pn : currentAllocation.keySet()) {
			for (Entry<FlowEntry, Long> entryFlowEntry : currentAllocation.get(pn)) {
				flowAllocation.put(entryFlowEntry.getKey(), pn);
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

			int selected = (int) (aggregation.size() * Math.random());
			portNumber = (PortNumber) aggregation.toArray()[selected];
		}
		return portNumber;
	}

	/**
	 *
	 * @param currentAllocation
	 *            Contains the original allocation on the current ports
	 * @return
	 */
	private Map<PortNumber, List<Entry<FlowEntry, Long>>> reallocateFlows(
			Map<PortNumber, List<Entry<FlowEntry, Long>>> currentAllocation, Set<PortNumber> linkPorts,
			double expectedLoad, int neededPorts, double bytesInterval) {
		double averageBytesPort = bytesInterval * expectedLoad / neededPorts;

		Map<PortNumber, PortStatistics> portOccupation = new HashMap<PortNumber, PortStatistics>();
		int count = 0;
		for (PortNumber pn : currentAllocation.keySet()) {
			if (count >= neededPorts) {
				break;
			}
			PortStatistics ps = new PortStatistics(null, pn, delay, portBandwidth, 0);
			for (Entry<FlowEntry, Long> entry : currentAllocation.get(pn)) {
				ps.addBytes(entry.getValue());
				ps.addFlow();
			}
			portOccupation.put(pn, ps);

			count += 1;
		}

		List<Entry<FlowEntry, Long>> flowsNeedReschedule = new ArrayList<Entry<FlowEntry, Long>>();

		for (PortNumber pn : linkPorts) {
			if (!portOccupation.containsKey(pn)) {
				flowsNeedReschedule.addAll(currentAllocation.get(pn));
				// Remove the port from the currentAllocation
				currentAllocation.remove(pn);
			}
		}

		// Sort flowsNeedReschedule in decreasing order
		Collections.sort(flowsNeedReschedule, flowComparatorDecreasing);

		// Distribute flows in the currently used ports, minimizing port
		// occupation.

		for (Entry<FlowEntry, Long> entryFlowEntry : flowsNeedReschedule) {
			Entry<PortNumber, PortStatistics> entry = Collections.min(portOccupation.entrySet(),
					portComparatorIncreasing);
			PortNumber pn = entry.getKey();
			portOccupation.get(pn).addBytes(entryFlowEntry.getValue());
			portOccupation.get(pn).addFlow();
			currentAllocation.get(pn).add(entryFlowEntry);
		}

		int iteration = 0;
		double improvement = 1;
		double lastQuality = computeAllocationQuality(portOccupation, averageBytesPort);

		// TODO: [future] Consider allowing getting worse (temporarily)
		// TODO: [future] Consider moving first flowsThatNeedReschedule before ports
		// actually assigned to that port
		while (improvement > 0 && iteration < MAX_FLOW_MODS) {
			// This print is for debug
			if (DEBUG) {
				System.err.println("Iteration " + iteration + " improvement: " + improvement);
			}
			// Get most idle port
			Entry<PortNumber, PortStatistics> entry = Collections.min(portOccupation.entrySet(),
					portComparatorIncreasing);
			PortNumber pn = entry.getKey();

			// Get capacity (c_pn) of most idle port (pn). c_pn = occupation[pn] -
			// averageBytesPort. Note: Necessarily, c_pn >= 0

			double pnCapacity = portOccupation.get(pn).getBytes() - averageBytesPort;

			// Get most occupied port with more than 1 flow (p1)
			PortNumber p1 = null;
			for (PortNumber portNumber : portOccupation.keySet()) {
				if (currentAllocation.get(portNumber).size() > 1 && (p1 == null
						|| portOccupation.get(portNumber).getBytes() > portOccupation.get(p1).getBytes())) {
					p1 = portNumber;
				}
			}

			if (p1 == null || p1.equals(pn)) {
				// Not possible improvement. End while.
				break;
			}

			// Get flow (f1) in p1 with the highest rate that is lower than or equal to c_pn
			// if possible.

			Entry<FlowEntry, Long> f1entry = null;
			int f1pos = 0;

			Entry<FlowEntry, Long> f1entryLowestRate = null;
			int f1posLowestRate = 0;

			// loop through the flows allocated to p1 (Note: its complexity is O(n) whereas
			// sorting the flows requires O(nlog(n)))
			for (int i = 0; i < currentAllocation.get(p1).size(); i++) {
				Entry<FlowEntry, Long> entryFlowEntry = currentAllocation.get(p1).get(i);
				if (entryFlowEntry.getValue() <= pnCapacity) {
					if (f1entry == null || entryFlowEntry.getValue() > f1entry.getValue()) {
						f1entry = entryFlowEntry;
						f1pos = i;
					}
				} else if (f1entryLowestRate == null || entryFlowEntry.getValue() < f1entryLowestRate.getValue()) {
					f1entryLowestRate = entryFlowEntry;
					f1posLowestRate = i;
				}
			}

			// If it was not possible, get the lowest-rate flow in p1 (f1). Note that,
			// f1 > c_pn.
			if (f1entry == null) {
				f1entry = f1entryLowestRate;
				f1pos = f1posLowestRate;
			}

			// Compute improvement of the tentative assignation
			double currentQuality = computeAllocationQuality(moveFlowTentative(portOccupation, p1, pn, f1entry),
					averageBytesPort);
			improvement = lastQuality - currentQuality;
			lastQuality = currentQuality;

			// Tentatively do the flow port movement. Only make it permanent in case
			// of improvement
			if (improvement > 0) {
				// Now, f1 can be allocated in pn. Move f1 from p1 to pn.
				moveFlow(currentAllocation, portOccupation, p1, pn, f1entry, f1pos);
			}

			iteration += 1;
		}
		return currentAllocation;
	}

	/**
	 * Moves a flow allocation from one port to another, updating the
	 * currentAllocation and portOccupation accordingly.
	 * 
	 * @param currentAllocation
	 * @param portOccupation
	 * @param fromPort
	 * @param toPort
	 * @param entryFlowEntry
	 *            The flow to be moved
	 * @param flowPos
	 *            Index of entryFlowEntry element in currentAllocation.get(fromPort)
	 *            list
	 */
	public void moveFlow(Map<PortNumber, List<Entry<FlowEntry, Long>>> currentAllocation,
			Map<PortNumber, PortStatistics> portOccupation, PortNumber fromPort, PortNumber toPort,
			Entry<FlowEntry, Long> entryFlowEntry, int flowPos) {
		// Add entryFlowEntry to toPort
		portOccupation.get(toPort).setBytes(portOccupation.get(toPort).getBytes() + entryFlowEntry.getValue());
		portOccupation.get(toPort).addFlow();
		currentAllocation.get(toPort).add(entryFlowEntry);

		// Remove entryFlowEntry from fromPort
		portOccupation.get(fromPort).setBytes(portOccupation.get(fromPort).getBytes() - entryFlowEntry.getValue());
		portOccupation.get(fromPort).removeFlow();
		currentAllocation.get(fromPort).remove(flowPos);
	}

	public Map<PortNumber, PortStatistics> moveFlowTentative(Map<PortNumber, PortStatistics> portOccupation,
			PortNumber fromPort, PortNumber toPort, Entry<FlowEntry, Long> entryFlowEntry) {
		Map<PortNumber, PortStatistics> portOccupationTentative = new HashMap<PortNumber, PortStatistics>(
				portOccupation);
		portOccupationTentative.get(toPort)
				.setBytes(portOccupationTentative.get(toPort).getBytes() + entryFlowEntry.getValue());
		portOccupationTentative.get(toPort).addFlow();

		portOccupationTentative.get(fromPort)
				.setBytes(portOccupationTentative.get(fromPort).getBytes() - entryFlowEntry.getValue());
		portOccupationTentative.get(fromPort).removeFlow();
		return portOccupationTentative;
	}

	/**
	 * @param portOccupation
	 * @param averageBytesPort
	 * @return The lowest, the better. 0 is the best possible
	 */
	public double computeAllocationQuality(Map<PortNumber, PortStatistics> portOccupation, double averageBytesPort) {
		double quality = 0;
		for (PortStatistics ps : portOccupation.values()) {
			quality += Math.pow(Math.max(ps.getBytes() - averageBytesPort, 0), 2);
		}
		return quality;
	}

	public Map<FlowEntry, PortNumber> computeAllocationAlg3(Map<FlowEntry, Long> flowMap, Set<PortNumber> linkPorts) {
		Map<FlowEntry, PortNumber> flowAllocation = new HashMap<FlowEntry, PortNumber>();

		double expectedLoad = computeExpectedLoad(flowMap);
		lastNeededPorts = (int) Math.ceil(expectedLoad + EXPECTED_LOAD_MARGIN);
		if (DEBUG) {
			System.out.println("_neededPorts = " + lastNeededPorts);
		}

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

}
