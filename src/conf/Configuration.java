package conf;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import algorithm.*;
import utils.FileNameUtils;

public class Configuration {

	// DEFAULT_VALUES
	public final String DEFAULT_INPUT_FILE = "trace_complete.txt";
	public final String DEFAULT_ALGORITHM = "3";
	public final String DEFAULT_NUM_PORTS = "5";
	public final String DEFAULT_SAMPLING_PERIOD = "0.5";
	public final String DEFAULT_FR_TIMEOUT = "30";
	public final String DEFAULT_START_BIT_DST_IP = "0";
	public final String DEFAULT_END_BIT_DST_IP = "8";
	public final String DEFAULT_QUEUE_SIZE = "0.01";
	public final String DEFAULT_SPEED = "1";

	private PrintStream printStream;
	private int iterationsToDiscard = 1;

	// Configurable parameters
	private String inputFile;
	private Class<? extends BaseAlgorithm> algorithm = new Algorithm3().getClass();
	private int numPorts;
	private double period;
	private double flowRuleTimeout;
	private int startBitDstIp;
	private int endBitDstIp;
	private double queueSize;
	private double speed;

	private String outputFile;

	public Configuration(String args[]) {
		parse(args);
		init();
	}

	/**
	 * Returns a mapping from command line algorithm argument value to algorithm
	 * Class.
	 * 
	 * @return
	 */
	public Map<String, Class<? extends BaseAlgorithm>> getAlgorithmsAvailable() {
		Map<String, Class<? extends BaseAlgorithm>> algorithmsAvailable = new HashMap<String, Class<? extends BaseAlgorithm>>();
		algorithmsAvailable.put("0", Algorithm0.class);
		algorithmsAvailable.put("1", Algorithm1.class);
		algorithmsAvailable.put("2", Algorithm2.class);
		algorithmsAvailable.put("3", Algorithm3.class);
		algorithmsAvailable.put("4", Algorithm4.class);
		algorithmsAvailable.put("5", Algorithm5.class);
		algorithmsAvailable.put("6", Algorithm6.class);
		algorithmsAvailable.put("7", Algorithm7.class);
		algorithmsAvailable.put("8", Algorithm8.class);
		algorithmsAvailable.put("9", Algorithm9.class);
		// TODO: Include here your custom algorithm (e.g., algorithmsAvailable.put("X",
		// AlgorithmX.class);)

		return algorithmsAvailable;
	}

	/**
	 * Parses the algorithm command line option, setting the algorithm class
	 * accordingly.
	 * 
	 * @param algorithm
	 *            Algorithm command line value
	 */
	public void parseAlgorithmOption(String algorithm) {

		if (getAlgorithmsAvailable().containsKey(algorithm)) {
			this.algorithm = getAlgorithmsAvailable().get(algorithm);
		}
	}

	public void parse(String args[]) {
		Options options = new Options();

		Option inputFileOption = new Option("i", "input", true, "Specifies the input file.");
		inputFileOption.setRequired(true);
		inputFileOption.setArgName("INPUT");
		options.addOption(inputFileOption);

		Option algorithmOption = new Option("a", "algorithm", true, "Specifies the algorithm. Available algorithms: "
				+ String.join(", ", getAlgorithmsAvailable().keySet()) + " [default: 3].");
		algorithmOption.setRequired(false);
		algorithmOption.setArgName("ALGORITHM");
		options.addOption(algorithmOption);

		Option numPortsOption = new Option("n", "numPorts", true, "Specifies the number of ports. [default: 5].");
		numPortsOption.setRequired(false);
		numPortsOption.setArgName("PORTS");
		options.addOption(numPortsOption);

		Option periodOption = new Option("p", "period", true,
				"Specifies flow sampling period (seconds) [default: 0.5].");
		periodOption.setRequired(false);
		periodOption.setArgName("PERIOD");
		options.addOption(periodOption);

		Option flowRuleTimeoutOption = new Option("f", "flowRuleTimeout", true,
				"Specifies flow rule timeout (seconds) [default: 30].");
		flowRuleTimeoutOption.setRequired(false);
		flowRuleTimeoutOption.setArgName("TIMEOUT");
		options.addOption(flowRuleTimeoutOption);

		Option startBitDstIpOption = new Option("s", "startBitDstIp", true,
				"Specifies the first bit of the destination IP address that will be used to define the flows [default: 0].");
		startBitDstIpOption.setRequired(false);
		startBitDstIpOption.setArgName("BIT");
		options.addOption(startBitDstIpOption);

		Option endBitDstIpOption = new Option("e", "endBitDstIp", true,
				"Specifies the last bit of the destination IP address that will be used to define the flows [default: 8].");
		endBitDstIpOption.setRequired(false);
		endBitDstIpOption.setArgName("BIT");
		options.addOption(endBitDstIpOption);

		Option queueSizeOption = new Option("q", "queueSize", true,
				"Specifies the size of the queue of each port (seconds) [default: 0.01].");
		queueSizeOption.setRequired(false);
		queueSizeOption.setArgName("SIZE");
		options.addOption(queueSizeOption);

		Option speedOption = new Option("x", "speed", true, "Specifies the relative speed of the trace [default: 1].");
		speedOption.setRequired(false);
		speedOption.setArgName("SPEED");
		options.addOption(speedOption);

		Option helpOption = new Option("h", "help", false, "Shows this help menu.");
		helpOption.setRequired(false);
		options.addOption(helpOption);

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd = null;

		try {
			cmd = parser.parse(options, args);
			if (cmd.hasOption("h")) {
				formatter.printHelp("sdn-bundle-simulator", options, true);
				System.exit(1);
			}
		} catch (ParseException e) {
			formatter.printHelp("sdn-bundle-simulator", options, true);

			System.exit(1);
			return;
		}

		this.inputFile = cmd.getOptionValue("input", DEFAULT_INPUT_FILE);

		String algorithm = cmd.getOptionValue("algorithm", DEFAULT_ALGORITHM);

		parseAlgorithmOption(algorithm);

		this.numPorts = Integer.parseInt(cmd.getOptionValue("numPorts", DEFAULT_NUM_PORTS));

		this.period = Double.parseDouble(cmd.getOptionValue("period", DEFAULT_SAMPLING_PERIOD));

		this.flowRuleTimeout = Double.parseDouble(cmd.getOptionValue("flowRuleTimeout", DEFAULT_FR_TIMEOUT));

		this.startBitDstIp = Integer.parseInt(cmd.getOptionValue("startBitDstIp", DEFAULT_START_BIT_DST_IP));

		this.endBitDstIp = Integer.parseInt(cmd.getOptionValue("endBitDstIp", DEFAULT_END_BIT_DST_IP));

		this.queueSize = Double.parseDouble(cmd.getOptionValue("queueSize", DEFAULT_QUEUE_SIZE));

		this.speed = Double.parseDouble(cmd.getOptionValue("speed", DEFAULT_SPEED));
	}

	/**
	 * Important: Must be called after parse().
	 */
	public void init() {
		outputFile = FileNameUtils.BASE_PATH + FileNameUtils.generateOutputFileName(algorithm, inputFile, period,
				flowRuleTimeout, startBitDstIp, endBitDstIp, queueSize, speed, numPorts);
		try {
			printStream = new PrintStream(new FileOutputStream(outputFile));
		} catch (FileNotFoundException e) {
			System.err.println("Error with output file: " + outputFile + ". Using System.out.");
			printStream = System.out;
		}
	}

	public PrintStream getPrintStream() {
		return printStream;
	}

	public void setPrintStream(PrintStream printStream) {
		this.printStream = printStream;
	}

	public int getIterationsToDiscard() {
		return iterationsToDiscard;
	}

	public void setIterationsToDiscard(int iterationsToDiscard) {
		this.iterationsToDiscard = iterationsToDiscard;
	}

	public String getInputFile() {
		return inputFile;
	}

	public void setInputFile(String inputFile) {
		this.inputFile = inputFile;
	}

	public Class<? extends BaseAlgorithm> getAlgorithm() {
		return algorithm;
	}

	public void setAlgorithm(Class<? extends BaseAlgorithm> algorithm) {
		this.algorithm = algorithm;
	}

	public double getPeriod() {
		return period;
	}

	public void setPeriod(double period) {
		this.period = period;
	}

	public double getFlowRuleTimeout() {
		return flowRuleTimeout;
	}

	public void setFlowRuleTimeout(double flowRuleTimeout) {
		this.flowRuleTimeout = flowRuleTimeout;
	}

	public int getStartBitDstIp() {
		return startBitDstIp;
	}

	public void setStartBitDstIp(int startBitDstIp) {
		this.startBitDstIp = startBitDstIp;
	}

	public int getEndBitDstIp() {
		return endBitDstIp;
	}

	public void setEndBitDstIp(int endBitDstIp) {
		this.endBitDstIp = endBitDstIp;
	}

	public double getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(double queueSize) {
		this.queueSize = queueSize;
	}

	public double getSpeed() {
		return speed;
	}

	public void setSpeed(double speed) {
		this.speed = speed;
	}

	public String getOutputFile() {
		return outputFile;
	}

	public void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
	}

	public int getNumPorts() {
		return numPorts;
	}

	public void setNumPorts(int numPorts) {
		this.numPorts = numPorts;
	}

}
