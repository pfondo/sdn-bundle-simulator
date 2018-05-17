package algorithm;

import java.util.ArrayList;
import java.util.Arrays;
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
 * Version 11: Compute expected number of ports needed (port occupation +
 * security margin). Compute the "best" allocation using a brute force algorithm.
 * 
 * @author pfondo
 *
 */
public class Algorithm11 extends BaseAlgorithm {

	private int lastNeededPorts = 0;

	private static final double EXPECTED_LOAD_MARGIN = 0.2;

	private double computeExpectedLoad(Map<FlowEntry, Long> flowMap) {
		double expectedLoad = 0;
		for (long flowBytes : flowMap.values()) {
			expectedLoad += flowBytes;
		}
		expectedLoad /= portBytesInterface;
		return expectedLoad;
	}

	public Map<FlowEntry, PortNumber> computeAllocation(Map<FlowEntry, Long> flowMap, Set<PortNumber> linkPorts) {
		return computeAllocationAlg11(flowMap, linkPorts);
	}

	public Map<FlowEntry, PortNumber> computeAllocationAlg11(Map<FlowEntry, Long> flowMap, Set<PortNumber> linkPorts) {
		double expectedLoad = computeExpectedLoad(flowMap);
		lastNeededPorts = (int) Math.ceil(expectedLoad + EXPECTED_LOAD_MARGIN);

		Map<FlowEntry, PortNumber> flowAllocation = new HashMap<FlowEntry, PortNumber>();
		Map<PortNumber, List<Entry<FlowEntry, Long>>> currentAllocation = new HashMap<PortNumber, List<Entry<FlowEntry, Long>>>();

		for (PortNumber pn : linkPorts) {
			currentAllocation.put(pn, new ArrayList<Entry<FlowEntry, Long>>());
		}

		// Compute current allocation
		for (Entry<FlowEntry, Long> entry : flowMap.entrySet()) {
			entry.setValue((long) (entry.getValue() * delay
					/ Math.min(entry.getKey().life(networkSimulator.getCurrentTime()), delay)));
			currentAllocation.get(entry.getKey().getOutputPort()).add(entry);
		}

		Map<PortNumber, List<Entry<FlowEntry, Long>>> newAllocation = reallocateFlowsBest(currentAllocation, linkPorts,
				expectedLoad, lastNeededPorts, getPortBytesAvailable(0));

		// Dump new allocation to flowAllocation result map (format conversion)
		for (PortNumber pn : newAllocation.keySet()) {
			for (Entry<FlowEntry, Long> entryFlowEntry : newAllocation.get(pn)) {
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
	 * Check all possibilities and get the one which minimizes energy consumption
	 * (maximizes quality of assignation) and flow modifications.
	 *
	 * @param currentAllocation
	 *            Contains the original allocation on the current ports
	 * @return
	 */
	private Map<PortNumber, List<Entry<FlowEntry, Long>>> reallocateFlowsBest(
			Map<PortNumber, List<Entry<FlowEntry, Long>>> currentAllocation, Set<PortNumber> linkPorts,
			double expectedLoad, int neededPorts, double bytesInterval) {

		int numFlows = 0;

		List<Entry<FlowEntry, Long>> flowList = new ArrayList<Entry<FlowEntry, Long>>();

		Map<PortNumber, PortStatistics> portOccupation = new HashMap<PortNumber, PortStatistics>();

		List<PortNumber> usedPorts = new ArrayList<PortNumber>();
		int count = 0;
		for (PortNumber pn : linkPorts) {
			if (count >= neededPorts) {
				break;
			}
			usedPorts.add(pn);
			portOccupation.put(pn, new PortStatistics(null, pn, delay, portBandwidth, 0));
			count += 1;
		}

		// Initially allocate all flows to the first port
		PortNumber port0 = usedPorts.get(0);
		PortStatistics ps = new PortStatistics(null, port0, delay, portBandwidth, 0);

		for (PortNumber pn : currentAllocation.keySet()) {
			for (Entry<FlowEntry, Long> entry : currentAllocation.get(pn)) {
				ps.addBytes(entry.getValue());
				ps.addFlow();
				flowList.add(entry);
				numFlows += 1;
			}
		}
		portOccupation.put(port0, ps);

		int[] currentState = new int[numFlows]; // Initialized to 0s

		// To store the best results
		int[] bestAllocation = currentState;
		double bestQuality = computeAllocationQuality(portOccupation);
		int bestNumFlowMods = numFlows; // This is not used initially
		long iteration = 0;

		boolean done = false;

		// Brute force test all possibilities

		while (!done) {
			iteration += 1;

			done = true;
			for (int i = 0; i < numFlows; i++) {
				if (currentState[i] == (neededPorts - 1)) {
					continue;
				}
				// currentState[i] must be incremented, and the previous ones set to 0
				currentState[i] += 1;
				moveFlow(portOccupation, usedPorts.get(currentState[i] - 1), usedPorts.get(currentState[i]),
						flowList.get(i));
				for (int j = 0; j < i; j++) {
					moveFlow(portOccupation, usedPorts.get(neededPorts - 1), usedPorts.get(0), flowList.get(j));
					currentState[j] = 0;
				}

				// TODO: This is a new state. Do the processing
				// System.err.println(Arrays.toString(currentState));
				if (iteration % 1000000000 == 0) {
					System.err.println("\titeration: " + iteration);
				}

				double currentQuality = computeAllocationQuality(portOccupation);
				if (currentQuality > bestQuality) {
					bestQuality = currentQuality;
					bestAllocation = currentState.clone();
					if (DEBUG) {
						System.err.println("Best allocation: " + Arrays.toString(bestAllocation));
						printPortOccupation(portOccupation);
						System.err.println("Best quality: " + bestQuality);
					}
				}

				done = false;
				break;
			}
		}

		// System.out.println("Number of iterations: " + iteration);

		return convertCurrentStateToAllocation(bestAllocation, usedPorts, flowList);
	}

	public void printPortOccupation(Map<PortNumber, PortStatistics> portOccupation) {
		String portOccupationString = "[ ";
		for (PortNumber pn : portOccupation.keySet()) {
			portOccupationString += portOccupation.get(pn).getBytes() + " ";
		}
		portOccupationString += "]";
		System.err.println("portOccupation: " + portOccupationString);
	}

	public Map<PortNumber, List<Entry<FlowEntry, Long>>> convertCurrentStateToAllocation(int[] currentState,
			List<PortNumber> usedPorts, List<Entry<FlowEntry, Long>> flowList) {
		Map<PortNumber, List<Entry<FlowEntry, Long>>> proposedAllocation = new HashMap<PortNumber, List<Entry<FlowEntry, Long>>>();
		for (PortNumber pn : usedPorts) {
			proposedAllocation.put(pn, new ArrayList<Entry<FlowEntry, Long>>());
		}
		for (int i = 0; i < currentState.length; i++) {
			proposedAllocation.get(usedPorts.get(currentState[i])).add(flowList.get(i));
		}
		return proposedAllocation;
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

	public void moveFlow(Map<PortNumber, PortStatistics> portOccupation, PortNumber fromPort, PortNumber toPort,
			Entry<FlowEntry, Long> entryFlowEntry) {
		portOccupation.get(toPort).setBytes(portOccupation.get(toPort).getBytes() + entryFlowEntry.getValue());
		portOccupation.get(toPort).addFlow();

		portOccupation.get(fromPort).setBytes(portOccupation.get(fromPort).getBytes() - entryFlowEntry.getValue());
		portOccupation.get(fromPort).removeFlow();
	}

	/**
	 * Compute quality of the allocation using Jain's fairness index.
	 * 
	 * @param portOccupation
	 * @param averageBytesPort
	 * @return The higher, the better. n is the best, 1/n is the worst.
	 */
	public double computeAllocationQuality(Map<PortNumber, PortStatistics> portOccupation) {
		double sum = 0;
		double squaresSum = 0;
		for (PortStatistics ps : portOccupation.values()) {
			sum += (double) (ps.getBytes());
			squaresSum += Math.pow((ps.getBytes()), 2);
		}
		double quality = Math.pow(sum, 2) / (portOccupation.size() * squaresSum);
		return quality;
	}

	/**
	 * @param portOccupation
	 * @param averageBytesPort
	 * @return The lower, the better. 0 is the best possible
	 */
	public double computeAllocationQualityOld(Map<PortNumber, PortStatistics> portOccupation, double averageBytesPort) {
		double quality = 0;
		for (PortStatistics ps : portOccupation.values()) {
			quality += Math.pow(Math.max(ps.getBytes() - averageBytesPort, 0), 2);
		}
		return quality;
	}

}
