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
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import algorithm.PortStatistics;
import algorithm.BaseAlgorithm;
import algorithm.LowLatencyBaseAlgorithm;
import auxiliar.PortNumber;
import auxiliar.Queue;
import conf.Configuration;
import utils.DecimalFormatUtils;
import utils.FileNameUtils;
import auxiliar.DeviceId;
import auxiliar.FlowEntry;
import auxiliar.Packet;

public class NetworkSimulator {

	private static final boolean DEBUG = false;
	private static final boolean WITH_TABS = true;

	private long period; // nanoseconds
	private long flowRuleTimeout; // nanoseconds
	private int numPorts;

	private long queueSize; // seconds

	private long iteration = 0;
	private BufferedReader br = null;
	private String line = "";
	private boolean finished = false;

	// Used to identify each flow
	private int startBitDstIp = 0;
	private int endBitDstIp = 0;

	private double speed = 0;

	private double alphaEwma;

	private int iterationsToDiscard;

	private String fileToAppendFinalResults;

	private String inputFile;

	private PrintStream printStream;

	private BaseAlgorithm algorithm;

	private LowLatencyBaseAlgorithm lowLatencyAlgorithm;

	private long numFlowMods;

	private double accRateError;

	private long accAlgorithmExecutionTime;

	private long lastArrivalTimestamp = 0;

	private Map<DeviceId, Map<PortNumber, PortStatistics>> totalPortStatistics;

	/**
	 * 
	 * @param algorithmClass
	 * @param inputFile
	 * @param period
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
	public NetworkSimulator(Class<? extends BaseAlgorithm> algorithmClass, String inputFile, long period,
			long flowRuleTimeout, int startBitDstIp, int endBitDstIp, PrintStream printStream, long queueSize,
			String fileToAppendFinalResults, int iterationsToDiscard, double speed,
			Class<? extends LowLatencyBaseAlgorithm> lowLatencyAlgorithmClass, double alphaEwma) {
		this.inputFile = inputFile;
		iteration = 0;
		finished = false;
		line = "";
		numFlowMods = 0;
		totalPortStatistics = new HashMap<DeviceId, Map<PortNumber, PortStatistics>>();
		br = null;
		try {
			br = new BufferedReader(new FileReader(inputFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		this.algorithm = BaseAlgorithm.newInstance(algorithmClass);
		this.lowLatencyAlgorithm = LowLatencyBaseAlgorithm.newInstance(lowLatencyAlgorithmClass);
		this.period = period;
		this.flowRuleTimeout = flowRuleTimeout;
		this.startBitDstIp = startBitDstIp;
		this.endBitDstIp = endBitDstIp;
		this.printStream = printStream;
		this.fileToAppendFinalResults = fileToAppendFinalResults;
		this.queueSize = queueSize;
		this.speed = speed;
		this.alphaEwma = alphaEwma;
		this.iterationsToDiscard = iterationsToDiscard;
		this.accAlgorithmExecutionTime = 0;
		this.accRateError = 0;
		// Must be called at the end of this constructor
		this.algorithm.init(this);
	}

	public NetworkSimulator(Configuration conf) {
		this.inputFile = conf.getInputFile();
		iteration = 0;
		finished = false;
		line = "";
		numFlowMods = 0;
		totalPortStatistics = new HashMap<DeviceId, Map<PortNumber, PortStatistics>>();
		br = null;
		try {
			br = new BufferedReader(new FileReader(this.inputFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		this.algorithm = BaseAlgorithm.newInstance(conf.getAlgorithm());
		this.lowLatencyAlgorithm = LowLatencyBaseAlgorithm.newInstance(conf.getLowLatencyAlgorithm());
		this.numPorts = conf.getNumPorts();
		this.period = conf.getPeriod();
		this.flowRuleTimeout = conf.getFlowRuleTimeout();
		this.startBitDstIp = conf.getStartBitDstIp();
		this.endBitDstIp = conf.getEndBitDstIp();
		this.printStream = conf.getPrintStream();
		this.fileToAppendFinalResults = null;
		this.queueSize = conf.getQueueSize();
		this.speed = conf.getSpeed();
		this.iterationsToDiscard = conf.getIterationsToDiscard();
		this.alphaEwma = conf.getAlphaEwma();
		this.accAlgorithmExecutionTime = 0;
		this.accRateError = 0;
		// Must be called at the end of this constructor
		this.algorithm.init(this);
		System.err.println("Executing simulation: " + conf.getOutputFile());
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
					new PortStatistics(FileNameUtils.generateOutputFileName(algorithm.getClass(), inputFile, period,
							flowRuleTimeout, startBitDstIp, endBitDstIp, queueSize, speed, numPorts,
							lowLatencyAlgorithm.getClass(), alphaEwma), pn, period, PORT_BANDWIDTH, queueSize));
		}
	}

	public String computeId(String srcIP, String dstIP, boolean isLowLatency) {
		String id = isLowLatency ? "1" : "0";
		int num;

		String dstIPBits = "";

		for (String numStr : dstIP.split("\\.")) {
			num = Integer.parseInt(numStr);
			dstIPBits += String.format("%08d", Integer.parseInt(Integer.toBinaryString(num)));
		}

		id += dstIPBits.substring(startBitDstIp, endBitDstIp);

		return id;
	}

	// This is a method used for debugging
	public void printFlowStatistics(Collection<FlowEntry> flowEntries) {
		for (FlowEntry flowEntry : flowEntries) {
			printStream.println("Flow " + flowEntry.getId() + ": " + "port " + flowEntry.getOutputPort().toLong() + "; "
					+ flowEntry.bytes() + " bytes");
		}
	}

	public void printPortStatistics(DeviceId deviceId, Set<PortNumber> portList, Map<PortNumber, Long> numFlowsPerPort,
			long flowMods, long algorithmExecutionTime, double rateError, double PORT_BANDWIDTH) {
		Map<PortNumber, PortStatistics> portOccupation = new HashMap<PortNumber, PortStatistics>();

		double averageConsumption = 0;
		for (PortNumber pn : portList) {
			PortStatistics ps = totalPortStatistics.get(deviceId).get(pn);
			portOccupation.put(pn, ps);
		}
		for (PortNumber currentPort : numFlowsPerPort.keySet()) {
			portOccupation.get(currentPort).setNumFlowsInterval(numFlowsPerPort.get(currentPort));
		}

		// Reset interval counters since this interval has ended
		boolean mustDiscard = iteration <= iterationsToDiscard;
		for (PortNumber pn : portOccupation.keySet()) {
			averageConsumption += portOccupation.get(pn).getEnergyConsumption(true);
			printStream.println(portOccupation.get(pn).toStringInterval());
			totalPortStatistics.get(deviceId).get(pn).finishInterval(mustDiscard, iteration * period);
		}
		averageConsumption /= portList.size();
		printStream.println("Average consumption: "
				+ DecimalFormatUtils.getDecimalFormat4().format(averageConsumption * 100) + " %");

		// Now: Discarding flowMods of this interval if this interval must be discarded
		if (!mustDiscard) {
			numFlowMods += flowMods;
			accAlgorithmExecutionTime += algorithmExecutionTime;
			accRateError += rateError;
		}

		printStream.println("Num flow mods: " + flowMods);
		printStream.println("Algorithm execution time: " + (algorithmExecutionTime / 1e6) + " ms");
		// printQueueStatistics();
	}

	public void printFinalPortStatistics(DeviceId deviceId, Set<PortNumber> portList, double PORT_BANDWIDTH) {
		double accumulatedConsumptionModel = 0;

		double accumulatedConsumptionReal = 0;

		double averageRate = 0;

		double totalLostPackets = 0;
		double totalPackets = 0;

		double accumulatedDelay = 0;
		long totalPacketsToComputeDelay = 0;

		double accumulatedDelayLowLatency = 0;
		long totalPacketsToComputeDelayLowLatency = 0;

		for (PortNumber pn : portList) {
			PortStatistics ps = totalPortStatistics.get(deviceId).get(pn);
			// Set current time of the simulation
			ps.setTime(getCurrentTime() - (period * iterationsToDiscard));
			totalPackets += ps.getNumPackets();
			accumulatedConsumptionModel += ps.getEnergyConsumption(false);
			averageRate += ps.getRate(false);

			// Update queue of this port to the final instant
			Queue queue = totalPortStatistics.get(deviceId).get(pn).getQueue();
			queue.update(getCurrentTime() + queueSize); // Enough time to transmit all the queued packets
			// Do final processing in queue
			queue.finishQueue(getCurrentTime());

			// The following two lines are deprecated
			// double idleFraction = queue.getIdleTime() / (lastArrivalTimestamp - period *
			// iterationsToDiscard);
			// double portRealConsumption = idleFraction * 0.1 + (1 - idleFraction) * 1;

			accumulatedConsumptionReal += ps.getRealEnergyConsumption(false);

			accumulatedDelay += queue.getAccumulatedDelay();
			totalPacketsToComputeDelay += queue.getNumPackets();
			// System.out.println("[DEBUG] Port " + pn.toLong() + ":");
			// System.out.println("[DEBUG] Accumulated delay: " +
			// DecimalFormatUtils.getDecimalFormat4().format(accumulatedDelay * 1e6));
			// System.out.println("[DEBUG] Num packets: " + totalPacketsToComputeDelay);

			accumulatedDelayLowLatency += queue.getAccumulatedDelayLowLatency();
			totalPacketsToComputeDelayLowLatency += queue.getNumPacketsLowLatency();

			totalLostPackets += queue.getNumExceeded();
			printStream.println(ps);
		}

		//System.out.println("Accumulated total delay: " + (accumulatedDelay + accumulatedDelayLowLatency));

		double averageConsumptionModel = accumulatedConsumptionModel / portList.size();
		double averageConsumptionReal = accumulatedConsumptionReal / portList.size();
		double averageDelay = accumulatedDelay / totalPacketsToComputeDelay;
		double averageDelayLowLatency = accumulatedDelayLowLatency / totalPacketsToComputeDelayLowLatency;

		DecimalFormat df = DecimalFormatUtils.getDecimalFormat4Pad0();

		printStream.println("Average consumption (model): " + df.format(averageConsumptionModel * 100.0) + " %");
		printStream.println("Average consumption (real): " + df.format(averageConsumptionReal * 100.0) + " %");
		printStream.println("Total loss percent: " + df.format(totalLostPackets * 100.0 / totalPackets) + " %");
		// Print num flow mods
		printStream.println("Num flow mods: " + numFlowMods);

		// Print algorithm execution time
		printStream.println("Average algorithm execution time: "
				+ df.format((accAlgorithmExecutionTime / ((double) 1e6 * (iteration - iterationsToDiscard)))) + " ms");

		String finalResult = "";
		// input filename
		finalResult += inputFile + (WITH_TABS ? "\t" : " ");
		// algorithm
		finalResult += algorithm.getClass().getSimpleName() + (WITH_TABS ? "\t" : " ");
		// low-latency algorithm
		finalResult += lowLatencyAlgorithm.getClass().getSimpleName() + (WITH_TABS ? "\t" : " ");
		// sampling period in seconds
		finalResult += df.format(period/1e9) + (WITH_TABS ? "\t\t" : " ");
		// range of bits used to identify the flows
		finalResult += startBitDstIp + "-" + endBitDstIp + (WITH_TABS ? "\t" : " ");
		// size of the buffer in milliseconds
		finalResult += df.format(queueSize / 1e6) + (WITH_TABS ? "\t\t" : " ");
		// speed of the trace
		finalResult += df.format(speed) + (WITH_TABS ? "\t" : " ");
		// num ports of the bundle
		finalResult += numPorts + (WITH_TABS ? "\t" : " ");
		// alphaEWMA value
		finalResult += alphaEwma + (WITH_TABS ? "\t" : " ");
		// average rate in Mbps
		finalResult += df.format(averageRate) + (WITH_TABS ? "\t" : " ");
		// total number of flow mods (per interval)
		finalResult += df.format((numFlowMods / ((double) (iteration - iterationsToDiscard))))
				+ (WITH_TABS ? "\t\t" : " ");
		// average algorithm execution time
		finalResult += df.format((accAlgorithmExecutionTime / ((double) 1e6 * (iteration - iterationsToDiscard))))
				+ (WITH_TABS ? "\t\t\t" : " ");
		// total loss percent
		finalResult += df.format(totalLostPackets * 100.0 / totalPackets) + (WITH_TABS ? "\t" : " ");
		// model energy consumption percent
		finalResult += df.format(averageConsumptionModel * 100.0) + (WITH_TABS ? "\t\t" : " ");
		// real energy consumption percent
		finalResult += df.format(averageConsumptionReal * 100.0) + (WITH_TABS ? "\t\t" : " ");
		// average error rate execution time
		finalResult += df.format((accRateError / ((double) (iteration - iterationsToDiscard))))
				+ (WITH_TABS ? "\t\t\t" : " ");
		// average delay of the packets
		finalResult += df.format(averageDelay / 1e3) + (WITH_TABS ? "\t\t" : " ");
		// average delay of the low-latency packets
		if (totalPacketsToComputeDelayLowLatency > 0) {
			finalResult += df.format(averageDelayLowLatency / 1e3);
		}
		finalResult += "\n";

		String header = "# file" + (WITH_TABS ? "\t\t\t" : " ") + "algorithm" + (WITH_TABS ? "\t" : " ")
				+ "low_latency_algorithm" + (WITH_TABS ? "\t" : " ") + "period(s)" + (WITH_TABS ? "\t" : " ") + "bits"
				+ (WITH_TABS ? "\t" : " ") + "buffer(ms)" + (WITH_TABS ? "\t" : " ") + "speed"
				+ (WITH_TABS ? "\t" : " ") + "ports" + (WITH_TABS ? "\t" : " ") + "alpha" + (WITH_TABS ? "\t" : " ")
				+ "rate(Mbps)" + (WITH_TABS ? "\t" : " ") + "flow_mods(int)" + (WITH_TABS ? "\t" : " ")
				+ "avg_time_alg(ms)" + (WITH_TABS ? "\t" : " ") + "loss(%)" + (WITH_TABS ? "\t" : " ")
				+ "model_energy(%)" + (WITH_TABS ? "\t" : " ") + "real_energy(%)" + (WITH_TABS ? "\t" : " ")
				+ "estimation_error(Mbps)" + (WITH_TABS ? "\t" : " ") + "avg_delay(us)";

		if (totalPacketsToComputeDelayLowLatency > 0) {
			header += (WITH_TABS ? "\t" : " ") + "avg_delay_low_latency(us)";
		}
		header += "\n";

		if (fileToAppendFinalResults != null) {
			// Legacy compatibility, not being used right now. Currently, only writing to
			// standard output.
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
			// DEBUG
			System.err.println(header + finalResult);
		}
		if (totalPacketsToComputeDelayLowLatency > 0) {
			// DEBUG
			System.err.println("Average delay (us): " + df.format(averageDelay / 1e3));
			System.err.println("Average delay of low-latency packets (us): " + df.format(averageDelayLowLatency / 1e3));
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

	public long getCurrentTime() {
		// double currentTime = iteration * period;
		long currentTime = lastArrivalTimestamp;
		return currentTime;
	}

	/**
	 * Returns: 0 if normal processing. 1 if the EOF has been reached. -1 if the
	 * line does not correspond to the current interval.
	 */
	private int processLine(String line, Map<String, FlowEntry> currentFlows, DeviceId deviceId,
			double PORT_BANDWIDTH) {
		boolean isLowLatency = false;
		// Allow comment lines starting with #
		if (line == null) {
			return 1;
		}
		if (line.startsWith("#")) {
			return 0;
		}

		long time; // In nanoseconds
		String sip;
		String dip;
		int bytes;

		try {
			String[] splittedLine = line.split(" ");
			time = (long) (1e9 * Double.parseDouble(splittedLine[0]) / speed);
			sip = splittedLine[1];
			dip = splittedLine[2];
			bytes = Integer.parseInt(splittedLine[3]);
			isLowLatency = splittedLine.length > 4; // If the line has more than 4 items then it is a low-latency packet
		} catch (Exception e) {
			System.err.println("Error on line: " + line);
			e.printStackTrace();
			return 1;
		}

		if (DEBUG) {
			System.err.println(time + " " + sip + " " + dip + " " + bytes);
		}

		if (time > (iteration * period)) {
			// This interval has ended
			if (DEBUG) {
				printStream.println("This interval has ended");
			}
			return -1;
		}

		// Do the processing
		lastArrivalTimestamp = time;

		String id = computeId(sip, dip, isLowLatency);
		FlowEntry matchFlow = null;
		if (currentFlows.containsKey(id)) {
			// Already present
			matchFlow = currentFlows.get(id);
			currentFlows.get(id).setBytes(currentFlows.get(id).bytes() + bytes);
			currentFlows.get(id).setLastUse(time);
		} else {
			// No present in the last poll: Allocate new port
			if (isLowLatency) {
				matchFlow = new FlowEntry(id, algorithm.selectOutputPortLowLatency(new DeviceId(1), new DeviceId(2)),
						bytes, time, time, true);
			} else {
				matchFlow = new FlowEntry(id, algorithm.selectOutputPort(new DeviceId(1), new DeviceId(2)), bytes, time,
						time, false);
			}
			currentFlows.put(id, matchFlow);
		}
		PortNumber selectedPort = matchFlow.getOutputPort();
		totalPortStatistics.get(deviceId).get(selectedPort).addPackets(1);
		totalPortStatistics.get(deviceId).get(selectedPort).addBytes(bytes);
		Queue queue = totalPortStatistics.get(deviceId).get(selectedPort).getQueue();
		if (Queue.DEBUG) {
			printStream.println("[DEBUG] port=" + matchFlow.getOutputPort() + ", currentTimestamp=" + time);
		}
		queue.update(time);
		Packet packet = new Packet(bytes, PORT_BANDWIDTH, time, isLowLatency);
		queue.addPacket(packet, isLowLatency, lowLatencyAlgorithm.getQueueType());

		if (Queue.DEBUG) {
			printStream.print("Added packet to queue of port " + matchFlow.getOutputPort() + "...");
			Scanner s = new Scanner(System.in);
			s.nextLine();
		}
		return 0;
	}

	public boolean isFinished() {
		return finished;
	}

	public void setFinished(boolean finished) {
		this.finished = finished;
	}

	public long getPeriod() {
		return period;
	}

	public void setPeriod(long period) {
		this.period = period;
	}

	public long getFlowRuleTimeout() {
		return flowRuleTimeout;
	}

	public void setFlowRuleTimeout(long flowRuleTimeout) {
		this.flowRuleTimeout = flowRuleTimeout;
	}

	public PrintStream getPrintStream() {
		return printStream;
	}

	public void setPrintStream(PrintStream printStream) {
		this.printStream = printStream;
	}

	public long getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(long queueSize) {
		this.queueSize = queueSize;
	}

	public int getNumPorts() {
		return numPorts;
	}

	public void setNumPorts(int numPorts) {
		this.numPorts = numPorts;
	}

	public LowLatencyBaseAlgorithm getLowLatencyAlgorithm() {
		return lowLatencyAlgorithm;
	}

	public void setLowLatencyAlgorithm(LowLatencyBaseAlgorithm lowLatencyAlgorithm) {
		this.lowLatencyAlgorithm = lowLatencyAlgorithm;
	}

	public double getAlphaEwma() {
		return alphaEwma;
	}

	public void setAlphaEwma(double alphaEwma) {
		this.alphaEwma = alphaEwma;
	}

}
