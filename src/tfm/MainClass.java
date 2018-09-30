package tfm;

import java.io.File;

import conf.Configuration;
import utils.FileNameUtils;

public class MainClass {

	public static void main(String[] args) {
		// Create directory if does not exist yet
		File directory = new File(FileNameUtils.BASE_PATH);
		if (!directory.exists()) {
			directory.mkdir();
		}
		singleExecution(args);
	}

	public static void singleExecution(String args[]) {
		// Set seed to allow for reproducibility
		Configuration conf = new Configuration(args);

		// Instantiate the simulator and start the simulation
		NetworkSimulator ns = new NetworkSimulator(conf);
		ns.schedule();
	}
}
