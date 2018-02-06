package tfm;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import algorithm.PortStatistics;
import algorithm.ReallocateFlowsTaskSimulator;
import auxiliar.PortNumber;
import auxiliar.Queue;
import utils.DecimalFormatUtils;
import utils.FileNameUtils;
import auxiliar.DeviceId;
import auxiliar.FlowEntry;
import auxiliar.Packet;

public class NetworkSimulator {

	private double period; // seconds
	private double flowRuleTimeout; // seconds
	private static final boolean DEBUG = false;

	private double queueSize; // seconds

	private long iteration = 0;
	private BufferedReader br = null;
	private String line = "";
	private boolean finished = false;

	// Used to identify each flow
	private int startBitDstIp = 0;
	private int endBitDstIp = 0;

	private int iterationsToDiscard;

	private String fileToAppendFinalResults;

	private String inputFile;
	private PrintStream printStream;

	private ReallocateFlowsTaskSimulator algorithm;

	private Map<DeviceId, Map<PortNumber, PortStatistics>> totalPortStatistics;

	/**
	 * 
	 * @param algorithmClass
	 * @param inputFile
	 * @param delay
	 *            (seconds)
	 * @param flowRuleTimeout
	 * @param startBitDstIp
	 * @param endBitDstIp
	 * @param printStream
	 * @param queueSize
	 *            (seconds)
	 * @param fileToAppendFinalResults
	 * @param iterationsToDiscard
	 */
	public NetworkSimulator(Class<? extends ReallocateFlowsTaskSimulator> algorithmClass, String inputFile,
			double delay, double flowRuleTimeout, int startBitDstIp, int endBitDstIp, PrintStream printStream,
			double queueSize, String fileToAppendFinalResults, int iterationsToDiscard) {
		this.inputFile = inputFile;
		iteration = 0;
		finished = false;
		line = "";
		totalPortStatistics = new HashMap<DeviceId, Map<PortNumber, PortStatistics>>();
		br = null;
		try {
			br = new BufferedReader(new FileReader(inputFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		this.algorithm = ReallocateFlowsTaskSimulator.newInstance(algorithmClass);
		this.period = delay;
		this.flowRuleTimeout = flowRuleTimeout;
		this.startBitDstIp = startBitDstIp;
		this.endBitDstIp = endBitDstIp;
		this.printStream = printStream;
		this.fileToAppendFinalResults = fileToAppendFinalResults;
		this.queueSize = queueSize;
		this.iterationsToDiscard = iterationsToDiscard;
		// Must be called at the end of this constructor
		this.algorithm.init(this);
	}

	public NetworkSimulator(Configuration conf) {
		this.inputFile = conf.inputFile;
		iteration = 0;
		finished = false;
		line = "";
		totalPortStatistics = new HashMap<DeviceId, Map<PortNumber, PortStatistics>>();
		br = null;
		try {
			br = new BufferedReader(new FileReader(this.inputFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		this.algorithm = ReallocateFlowsTaskSimulator.newInstance(conf.algorithm);
		this.period = conf.period;
		this.flowRuleTimeout = conf.flowRuleTimeout;
		this.startBitDstIp = conf.startBitDstIp;
		this.endBitDstIp = conf.endBitDstIp;
		this.printStream = conf.printStream;
		this.fileToAppendFinalResults = null;
		this.queueSize = conf.queueSize;
		this.iterationsToDiscard = conf.iterationsToDiscard;
		// Must be called at the end of this constructor
		this.algorithm.init(this);
	}

	public void schedule() {
		algorithm.schedule();
	}

	public void finish() {
		if (br != null) {
			try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		setFinished(true);
	}

	public void initPortStatistics(DeviceId deviceId, Set<PortNumber> portList, double PORT_BANDWIDTH) {
		totalPortStatistics.put(deviceId, new HashMap<PortNumber, PortStatistics>());
		for (PortNumber pn : portList) {
			totalPortStatistics.get(deviceId).put(pn,
					new PortStatistics(
							FileNameUtils.generateOutputFileName(algorithm.getClass(), inputFile, period,
									flowRuleTimeout, startBitDstIp, endBitDstIp, queueSize),
							pn, period, PORT_BANDWIDTH, queueSize));
		}
	}

	public String computeId(String srcIP, String dstIP) {
		String id = "";
		int num;

		String dstIPBits = "";

		for (String numStr : dstIP.split("\\.")) {
			num = Integer.parseInt(numStr);
			dstIPBits += String.format("%08d", Integer.parseInt(Integer.toBinaryString(num)));
		}

		id = dstIPBits.substring(startBitDstIp, endBitDstIp);
		return id;
	}

	// This is a method used for debugging
	public void printFlowStatistics(Collection<FlowEntry> flowEntries) {
		for (FlowEntry flowEntry : flowEntries) {
			printStream.println("Flow " + flowEntry.getId() + ": " + "port " + flowEntry.getOutputPort().toLong() + "; "
					+ flowEntry.bytes() + " bytes");
		}
	}

	public void printPortStatistics(DeviceId deviceId, Set<PortNumber> portList, Map<FlowEntry, Long> flowMap,
			double PORT_BANDWIDTH) {
		Map<PortNumber, PortStatistics> portOccupation = new HashMap<PortNumber, PortStatistics>();
		PortNumber currentPort;
		double averageConsumption = 0;
		for (PortNumber pn : portList) {
			PortStatistics ps = totalPortStatistics.get(deviceId).get(pn);
			portOccupation.put(pn, ps);
		}
		for (FlowEntry flowEntry : flowMap.keySet()) {
			currentPort = flowEntry.getOutputPort();
			portOccupation.get(currentPort).addFlow();
		}
		for (PortNumber pn : portOccupation.keySet()) {
			averageConsumption += portOccupation.get(pn).getEnergyConsumption(true);
			printStream.println(portOccupation.get(pn).toStringInterval());
			// Reset interval counters since this interval has ended
			boolean mustDiscard = iteration <= iterationsToDiscard;
			totalPortStatistics.get(deviceId).get(pn).finishInterval(mustDiscard, iteration * period);
		}
		averageConsumption /= portList.size();
		printStream.println("Average consumption: "
				+ DecimalFormatUtils.getDecimalFormat4().format(averageConsumption * 100) + " %");
		// printQueueStatistics();
	}

	public void printFinalPortStatistics(DeviceId deviceId, Set<PortNumber> portList, double PORT_BANDWIDTH) {
		double averageConsumption = 0;
		double averageRate = 0;
		double totalLostPackets = 0;
		double totalPackets = 0;
		double accumulatedDelay = 0;
		long totalPacketsToComputeDelay = 0;
		for (PortNumber pn : portList) {
			PortStatistics ps = totalPortStatistics.get(deviceId).get(pn);
			// Set current time of the simulation
			ps.setTime(getCurrentTime() - (period * iterationsToDiscard));
			totalPackets += ps.getNumPackets();
			averageConsumption += ps.getEnergyConsumption(false);
			averageRate += ps.getRate(false);

			// Update queue of this port to the final instant
			Queue queue = totalPortStatistics.get(deviceId).get(pn).getQueue();
			accumulatedDelay += queue.getAccumulatedDelay();
			totalPacketsToComputeDelay += queue.getNumPackets();
			queue.update(period * iteration);

			// Do final processing in queue
			queue.finishQueue();

			totalLostPackets += queue.getNumExceeded();
			printStream.println(ps);
		}
		averageConsumption /= portList.size();
		double averageDelay = accumulatedDelay / totalPacketsToComputeDelay;

		DecimalFormat df = DecimalFormatUtils.getDecimalFormat4();

		printStream.println("Average consumption: " + df.format(averageConsumption * 100.0) + " %");
		printStream.println("Total loss percent: " + df.format(totalLostPackets * 100.0 / totalPackets) + " %");
		String finalResult = "";
		finalResult += inputFile + " "; // input filename
		finalResult += algorithm.getClass().getSimpleName() + " "; // algorithm
		finalResult += df.format(period) + " "; // sampling period in seconds
		// range of bits used to identify the flows
		finalResult += startBitDstIp + "-" + endBitDstIp + " ";
		finalResult += df.format(queueSize * 1e3) + " "; // size of the buffer in packets
		// total loss percent
		finalResult += df.format(totalLostPackets * 100.0 / totalPackets) + " ";
		// average consumption percent
		finalResult += df.format(averageConsumption * 100.0) + " ";
		//
		finalResult += df.format(averageDelay * 1e6) + " ";
		// average rate in Mbps
		finalResult += df.format(averageRate) + " ";
		finalResult += "\n";

		String header = "# file algorithm period(s) bits buffer(ms) loss(%) energy(%) avg_delay(us) rate(Mbps)\n";

		if (fileToAppendFinalResults != null) {
			// Legacy compatibility. Now, only writing to standard output.
			synchronized (this) {
				try {
					Files.write(Paths.get(fileToAppendFinalResults), finalResult.getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					try {
						Files.write(Paths.get(fileToAppendFinalResults), header.getBytes(),
								StandardOpenOption.CREATE_NEW);
						Files.write(Paths.get(fileToAppendFinalResults), finalResult.getBytes(),
								StandardOpenOption.APPEND);
					} catch (IOException e2) {
						System.err.println("Error writing file: " + fileToAppendFinalResults);
					}
				}
			}
		} else {
			// Preferred way
			System.out.println(header + finalResult);
		}
	}

	/* Returns null on empty set */
	public Collection<FlowEntry> getFlowEntries(DeviceId deviceId, Collection<FlowEntry> oldFlowEntries,
			double PORT_BANDWIDTH) {
		Map<String, FlowEntry> currentFlowsMap = new HashMap<String, FlowEntry>();
		if (!totalPortStatistics.containsKey(deviceId)) {
			initPortStatistics(deviceId, algorithm.getLinkPorts(deviceId, new DeviceId(2)), PORT_BANDWIDTH);
		}
		iteration += 1;
		printStream.println("# iteration " + iteration);
		// DEBUG
		System.err.println("# iteration " + iteration);

		if (oldFlowEntries != null) {
			for (FlowEntry fe : oldFlowEntries) {
				// Check if the flow has already expired
				if ((fe.getLastUse() + flowRuleTimeout) > (iteration * period)) {
					currentFlowsMap.put(fe.getId(), fe);
				}
			}
		}

		// Compute flowEntries reading from file
		try {
			if (line == "") {
				// i.e., it is the first time that this function is called
				try {
					line = br.readLine();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
			while (line != null) {
				try {
					if (processLine(line, currentFlowsMap, deviceId, PORT_BANDWIDTH) == -1) {
						break;
					}
					line = br.readLine();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
			if (line == null) {
				// The EOF has been reached
				finish();
				// Editing here: return currentFlowsMap.values();
				return null;
			} else {

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return currentFlowsMap.values();
	}

	public double getCurrentTime() {
		return iteration * period;
	}

	/**
	 * Returns: 0 if normal processing. 1 if the EOF has been reached. -1 if the
	 * line does not correspond to the current interval.
	 */
	private int processLine(String line, Map<String, FlowEntry> currentFlows, DeviceId deviceId,
			double PORT_BANDWIDTH) {
		// Allow comment lines starting with #
		if (line == null) {
			return 1;
		}
		if (line.startsWith("#")) {
			return 0;
		}

		double time;
		String sip;
		String dip;
		int bytes;

		try {
			String[] splittedLine = line.split(" ");
			// Debug divide time between into 10
			time = Double.parseDouble(splittedLine[0]) / 10;
			sip = splittedLine[1];
			dip = splittedLine[2];
			bytes = Integer.parseInt(splittedLine[3]);
		} catch (Exception e) {
			System.err.println("Error on line: " + line);
			e.printStackTrace();
			return 1;
		}

		if (DEBUG) {
			printStream.println(time + " " + sip + " " + dip + " " + bytes);
		}

		if (time > (iteration * period)) {
			// This interval has ended
			if (DEBUG) {
				printStream.println("This interval has ended");
			}
			return -1;
		}
		// Do the processing

		String id = computeId(sip, dip);
		FlowEntry matchFlow = null;
		if (currentFlows.containsKey(id)) {
			// Already present
			matchFlow = currentFlows.get(id);
			currentFlows.get(id).setBytes(currentFlows.get(id).bytes() + bytes);
			currentFlows.get(id).setLastUse(time);
		} else {
			// No present in the last poll: Allocate new port
			matchFlow = new FlowEntry(id, selectOutputPort(new DeviceId(1), new DeviceId(2)), bytes, time, time);
			currentFlows.put(id, matchFlow);
		}
		// IMPORTANT: Editing here!
		// portPackets.get(deviceId).put(matchFlow.getOutputPort(),
		// portPackets.get(deviceId).get(matchFlow.getOutputPort()) + 1);
		PortNumber selectedPort = matchFlow.getOutputPort();
		totalPortStatistics.get(deviceId).get(selectedPort).addPackets(1);
		totalPortStatistics.get(deviceId).get(selectedPort).addBytes(bytes);
		Queue queue = totalPortStatistics.get(deviceId).get(selectedPort).getQueue();
		if (Queue.DEBUG) {
			printStream.println("[DEBUG] port=" + matchFlow.getOutputPort() + ", currentTimestamp=" + time);
		}
		queue.update(time);
		Packet packet = new Packet(bytes, PORT_BANDWIDTH, time);
		queue.addPacket(packet);
		if (Queue.DEBUG) {
			printStream.print("Added packet to queue of port " + matchFlow.getOutputPort() + "...");
			Scanner s = new Scanner(System.in);
			s.nextLine();
		}
		return 0;
	}

	// As of 26/06/2017, returns a random port
	private PortNumber selectOutputPort(DeviceId src, DeviceId dstDevice) {

		PortNumber portNumber = null;
		if (dstDevice != null) {
			Set<PortNumber> aggregation = new TreeSet<PortNumber>(new Comparator<PortNumber>() {

				@Override
				public int compare(PortNumber o1, PortNumber o2) {
					return (int) Math.signum(o1.toLong() - o2.toLong());
				}

			});
			for (PortNumber pn : algorithm.getLinkPorts(src, dstDevice)) {
				aggregation.add(pn);
			}
			int selected = (int) (aggregation.size() * Math.random());
			portNumber = (PortNumber) aggregation.toArray()[selected];
		}
		return portNumber;
	}

	public boolean isFinished() {
		return finished;
	}

	public void setFinished(boolean finished) {
		this.finished = finished;
	}

	public double getDelay() {
		return period;
	}

	public void setDelay(double delay) {
		this.period = delay;
	}

	public double getFlowRuleTimeout() {
		return flowRuleTimeout;
	}

	public void setFlowRuleTimeout(double flowRuleTimeout) {
		this.flowRuleTimeout = flowRuleTimeout;
	}

	public PrintStream getPrintStream() {
		return printStream;
	}

	public void setPrintStream(PrintStream printStream) {
		this.printStream = printStream;
	}

	public double getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(double queueSize) {
		this.queueSize = queueSize;
	}

}
