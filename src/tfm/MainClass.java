package tfm;

import java.io.File;

public class MainClass {
	private final static String BASE_PATH = "results/";

	public static void main(String[] args) {
		// Create directory if does not already exist
		File directory = new File(BASE_PATH);
		if (!directory.exists()) {
			directory.mkdir();
		}
		singleExecution(args);
	}

	public static void singleExecution(String args[]) {
		Configuration conf = new Configuration(args);

		// Instantiate the simulator and start the simulation
		NetworkSimulator ns = new NetworkSimulator(conf);
		ns.schedule();
	}
}
