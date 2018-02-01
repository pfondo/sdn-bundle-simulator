package tfm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import algorithm.*;
import algorithm.ReallocateFlowsTaskSimulator;
import utils.DecimalFormatUtils;

public class MainClass {
	private final static String BASE_PATH = "results/";

	public static void main(String[] args) {
		// Create directory if does not already exist
		File directory = new File(BASE_PATH);
		if (!directory.exists()) {
			directory.mkdir();
		}
		//parametricExecution();
		singleExecution();
	}

	public static void parametricExecution() {
		// Fixed parameters
		PrintStream printStream;
		String fileToAppendFinalResults = BASE_PATH + "final_results.log";
		double flowRuleTimeout = 30;
		int startBitDstIp = 0;
		int endBitDstIp = 8;
		int iterationsToDiscard = 1;

		// Variable parameters

		List<String> inputFiles = new ArrayList<String>();
		// Fill inputFiles
		inputFiles.add("trace_complete.txt");

		List<Class<? extends ReallocateFlowsTaskSimulator>> algorithms = new ArrayList<Class<? extends ReallocateFlowsTaskSimulator>>();
		// Fill algorithms
		algorithms.add(new Algorithm1().getClass());
		algorithms.add(new Algorithm2().getClass());
		algorithms.add(new Algorithm3().getClass());
		algorithms.add(new Algorithm0().getClass());

		List<Double> delays = new ArrayList<Double>();
		// Fill delays
		for (double i = 0.01; i <= 1.0001; i += 0.01) {
			delays.add(i);
		}
		//delays.add(0.5);

		List<Double> queueSizes = new ArrayList<Double>();
		// Fill queueSizes
		//queueSizes.add(0.00001);
		//queueSizes.add(0.0001);
		//queueSizes.add(0.001);
		queueSizes.add(0.01);
		//queueSizes.add(0.1);

		for (String inputFile : inputFiles) {
			for (Class<? extends ReallocateFlowsTaskSimulator> algorithm : algorithms) {
				for (double delay : delays) {
					for (double queueSize : queueSizes) {
						String outputFile = generateOutputFileName(algorithm, inputFile, delay, flowRuleTimeout,
								startBitDstIp, endBitDstIp, queueSize);
						System.out.println("Executing simulation: " + outputFile);
						try {
							printStream = new PrintStream(new FileOutputStream(outputFile));
						} catch (FileNotFoundException e) {
							System.err.println("Error with output file: " + outputFile);
							printStream = System.out;
						}

						// Instantiate the simulator and start the simulation
						NetworkSimulator ns = new NetworkSimulator(algorithm, inputFile, delay, flowRuleTimeout,
								startBitDstIp, endBitDstIp, printStream, queueSize, fileToAppendFinalResults,
								iterationsToDiscard);
						ns.schedule();
					}
				}
			}
		}
	}

	public static void singleExecution() {
		PrintStream printStream;
		String fileToAppendFinalResults = BASE_PATH + "final_results_single_execution.log";
		int iterationsToDiscard = 1;

		// Configurable parameters
		Class<? extends ReallocateFlowsTaskSimulator> algorithm = new Algorithm1().getClass();
		String inputFile = "trace_complete.txt";
		double delay = 0.5;
		double flowRuleTimeout = 30;
		int startBitDstIp = 0;
		int endBitDstIp = 8;
		double queueSize = 0.01; // In seconds // 1.2 ms = ((1000 pkt * 1500 byte/pkt * 8 bit/byte)12 Mbit over a 10 Gb/s link)

		String outputFile = generateOutputFileName(algorithm, inputFile, delay, flowRuleTimeout, startBitDstIp,
				endBitDstIp, queueSize) + "_single_execution";
		System.err.println("Executing simulation: " + outputFile);
		try {
			printStream = new PrintStream(new FileOutputStream(outputFile));
		} catch (FileNotFoundException e) {
			System.err.println("Error with output file: " + outputFile);
			printStream = System.out;
		}

		// Instantiate the simulator and start the simulation
		NetworkSimulator ns = new NetworkSimulator(algorithm, inputFile, delay, flowRuleTimeout, startBitDstIp,
				endBitDstIp, printStream, queueSize, fileToAppendFinalResults, iterationsToDiscard);
		ns.schedule();
	}

	public static String generateOutputFileName(Class<? extends ReallocateFlowsTaskSimulator> algorithm,
			String inputFile, double delay, double flowRuleTimeout, int startBitDstIp, int endBitDstIp, double queue) {
		String toReturn = "";
		DecimalFormat df = DecimalFormatUtils.getDecimalFormat4();
		toReturn += BASE_PATH + "sim_alg=" + algorithm.getSimpleName() + "_inputFile=" + inputFile + "_delay="
				+ df.format(delay) + "_bits=" + startBitDstIp + "-" + endBitDstIp + "_queue=" + df.format(queue * 1e3)
				+ "ms";
		return toReturn;
	}
}
