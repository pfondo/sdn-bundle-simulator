package tfm;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import algorithm.*;
import algorithm.ReallocateFlowsTaskSimulator;
import utils.FileNameUtils;

public class Configuration {

	PrintStream printStream;
	int iterationsToDiscard = 1;

	// DEFAULT_VALUES
	String DEFAULT_INPUT_FILE = "trace_complete.txt";
	String DEFAULT_ALGORITHM = "3";
	String DEFAULT_SAMPLING_PERIOD = "0.5";
	String DEFAULT_FR_TIMEOUT = "30";
	String DEFAULT_START_BIT_DST_IP = "0";
	String DEFAULT_END_BIT_DST_IP = "8";
	String DEFAULT_QUEUE_SIZE = "0.01";

	// Configurable parameters
	String inputFile;
	Class<? extends ReallocateFlowsTaskSimulator> algorithm = new Algorithm3().getClass();
	double period = 0.5;
	double flowRuleTimeout = 30;
	int startBitDstIp = 0;
	int endBitDstIp = 8;
	double queueSize = 0.01; // In seconds // 1.2 ms = ((1000 pkt * 1500 byte/pkt * 8 bit/byte)12 Mbit over a
								// 10 Gb/s link)

	String outputFile;

	public Configuration(String args[]) {
		parse(args);
		init();
	}

	public void parse(String args[]) {
		Options options = new Options();

		Option inputFileOption = new Option("i", "input", true, "Specifies the input file.");
		inputFileOption.setRequired(true);
		inputFileOption.setArgName("INPUT");
		options.addOption(inputFileOption);

		Option algorithmOption = new Option("a", "algorithm", true,
				"Specifies the algorithm. Available algorithms: 0, 1, 2 or 3 [default: 3].");
		algorithmOption.setRequired(false);
		algorithmOption.setArgName("ALGORITHM");
		options.addOption(algorithmOption);

		Option periodOption = new Option("d", "period", true,
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

		Option helpOption = new Option("h", "help", false, "Shows this help menu.");
		helpOption.setRequired(false);
		options.addOption(helpOption);

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("sdn-bundle-simulator", options, true);

			System.exit(1);
			return;
		}

		this.inputFile = cmd.getOptionValue("input", DEFAULT_INPUT_FILE);

		String algorithm = cmd.getOptionValue("algorithm", DEFAULT_ALGORITHM);
		if (algorithm.equals("0")) {
			this.algorithm = new Algorithm0().getClass();
		} else if (algorithm.equals("1")) {
			this.algorithm = new Algorithm1().getClass();
		} else if (algorithm.equals("2")) {
			this.algorithm = new Algorithm2().getClass();
		} else if (algorithm.equals("3")) {
			this.algorithm = new Algorithm3().getClass();
		}

		this.period = Double.parseDouble(cmd.getOptionValue("period", DEFAULT_SAMPLING_PERIOD));

		this.flowRuleTimeout = Double.parseDouble(cmd.getOptionValue("flowRuleTimeout", DEFAULT_FR_TIMEOUT));

		this.startBitDstIp = Integer.parseInt(cmd.getOptionValue("startBitDstIp", DEFAULT_START_BIT_DST_IP));

		this.endBitDstIp = Integer.parseInt(cmd.getOptionValue("endBitDstIp", DEFAULT_END_BIT_DST_IP));

		this.queueSize = Double.parseDouble(cmd.getOptionValue("queueSize", DEFAULT_QUEUE_SIZE));
	}

	/**
	 * Important: Must be called after parse().
	 */
	public void init() {
		outputFile = FileNameUtils.BASE_PATH + FileNameUtils.generateOutputFileName(algorithm, inputFile, period,
				flowRuleTimeout, startBitDstIp, endBitDstIp, queueSize);
		try {
			printStream = new PrintStream(new FileOutputStream(outputFile));
		} catch (FileNotFoundException e) {
			System.err.println("Error with output file: " + outputFile + ". Using System.out.");
			printStream = System.out;
		}
	}

}
