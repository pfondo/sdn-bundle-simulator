# sdn-bundle-simulator

[![Build Status](https://travis-ci.org/pfondo/sdn-bundle-simulator.svg?branch=master)](https://travis-ci.org/pfondo/sdn-bundle-simulator)


## Description

This is a simulator of a bundle of Energy-Efficient Ethernet (EEE) links between two switches. It simulates an Software-Defined Networking (SDN) application which periodically queries the flows installed in the first switch and performs a new allocation of the flows to the ports of the bundle, that will be used during the next interval.

The simulator has been designed following the Open Network Operating System (ONOS) syntax in order to ease the migration of the algorithms to an operative ONOS application.

Flows are defined as a bitmask on the destination IP address.

## Features

* Modify the speed of the trace being evaluated.
* Calculate the average binary rate of the trace.
* Calculate the average delay of the packets.
* Calculate the number of flow modifications performed by our algorithm.
* Calculate the average consumption of the device.
* Calculate the packet loss.

## Compilation

    mvn clean package

## Execution

    usage: java -jar target/sdn-bundle-simulator-0.1-SNAPSHOT-jar-with-dependencies.jar
    	   [-a <ALGORITHM>] [-e <BIT>] [-f <TIMEOUT>] [-h] -i <INPUT> [-n <PORTS>]
    	   [-p <PERIOD>] [-q <SIZE>] [-s <BIT>] [-x <SPEED>]
     -a,--algorithm <ALGORITHM>       Specifies the algorithm. Available
                                      algorithms: 0, 1, 2 or 3 [default: 3].
     -e,--endBitDstIp <BIT>           Specifies the last bit of the
                                      destination IP address that will be used
                                      to define the flows [default: 8].
     -f,--flowRuleTimeout <TIMEOUT>   Specifies flow rule timeout (seconds)
                                      [default: 30].
     -h,--help                        Shows this help menu.
     -i,--input <INPUT>               Specifies the input file.
     -n,--numPorts <PORTS>            Specifies the number of ports. [default:
                                      5].
     -p,--period <PERIOD>             Specifies flow sampling period (seconds)
                                      [default: 0.5].
     -q,--queueSize <SIZE>            Specifies the size of the queue of each
                                      port (seconds) [default: 0.01].
     -s,--startBitDstIp <BIT>         Specifies the first bit of the
                                      destination IP address that will be used
                                      to define the flows [default: 0].
     -x,--speed <SPEED>               Specifies the relative speed of the
                                      trace [default: 1].

## Results

- The final results of the simulation are written to standard output.
- The standard error output is also used to inform of the parameters of the running simulation, and also its status.
- A "results" folder is created to store the detailed results of the simulation.
- Besides, a "packets" folder is created to store the packets transmitted by each port, of this simulation.

## Adding an algorithm

Adding a new algorithm to the simulator is simple. These are the main steps that should be followed:

1. Create your own class, say `AlgorithmX`, extending the `algorithm.BaseAlgorithm` abstract class. There are several examples in the algorithm package (e.g., `algorithm.Algorithm0`).
1. Implement the `Map<FlowEntry, PortNumber> computeAllocation(Map<FlowEntry, Long> flowMap, Set<PortNumber> linkPorts)` method in `AlgorithmX` using your own flow allocation logic.
1. If you wish, you can also find interesting implementing the following methods in `AlgorithmX` overriding the default implementation: `double getPortBytesAvailable(long numFlows)`, `PortNumber selectOutputPort(DeviceId src, DeviceId dst)` and `PortNumber selectOutputPortLowLatency(DeviceId src, DeviceId dstDevice)`.
1. If you wish, you can also modify how the expected number of bytes that each flow will transmit in the next interval are calculated, modifying the `algorithm.FlowBytesHistory` class.
1. In the `Map<String, Class<? extends BaseAlgorithm>> getAlgorithmsAvailable()` method of the `conf.Configuration` class, add a line to include your algorithm among the algorithms available (e.g., `algorithmsAvailable.put("X", AlgorithmX.class);`).
1. Then you can run the simulator using the `--algorithm X` argument.

## Copyright

Copyright ⓒ 2017–2018 Pablo Fondo Ferreiro <pfondo@det.uvigo.es>.

This simulator is licensed under the GNU General Public License, version 3 (GPL-3.0). For more information see LICENSE.txt