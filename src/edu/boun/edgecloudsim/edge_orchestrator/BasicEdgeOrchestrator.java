/*
 * Title:        EdgeCloudSim - Basic Edge Orchestrator implementation
 * 
 * Description: 
 * BasicEdgeOrchestrator implements basic algorithms which are
 * first/next/best/worst/random fit algorithms while assigning
 * requests to the edge devices.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_orchestrator;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.distribution.NormalDistribution;
import java.lang.Math;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.core.CloudSim;


import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.ETCMatrix;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.NormDistr;
import edu.boun.edgecloudsim.utils.SimLogger;

public class BasicEdgeOrchestrator extends EdgeOrchestrator {
	private int numberOfHost; //used by load balancer
	@SuppressWarnings("unused")
	private int lastSelectedHostIndex; //used by load balancer
	private int[] lastSelectedVmIndexes; //used by each host individually
	private static Datacenter receivingBS; // !!! IMPORTANT !!! DON'T USE THE METHOD Datacenter.getId(), it's messed up, use recBS instead if u need an index.
	private static int recBS = -1; //Receiving DC ID
	private static int finalBS = -1;
	public static int getRecBS() {
		return recBS;
	}

	public void setRecBS(int recBS) {
		this.recBS = recBS;
	}

	public static int getFinalBS() {
		return finalBS;
	}

	public void setFinalBS(int finalBS) {
		this.finalBS = finalBS;
	}

	private double probab = 0.0;
	private int bb;
	public BasicEdgeOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}

	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
		
		lastSelectedHostIndex = -1;
		lastSelectedVmIndexes = new int[numberOfHost];
		for(int i=0; i<numberOfHost; i++)
			lastSelectedVmIndexes[i] = -1;
	}
	
	

	@Override
	public int getDeviceToOffload(Task task) {
		
		int result = SimSettings.EDGE_ORCHESTRATOR_ID;
		if(simScenario.equals("SINGLE_TIER")){
			result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		}
		/* Calculate the location of receiving Base Station
		 * the closest one to the device(vehicle)
		 */
		
		Location deviceLoc = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
		int xdev = deviceLoc.getXPos();
		int ydev = deviceLoc.getYPos();
		
		List<Datacenter> datacenters = SimManager.getInstance().edgeServerManager.getDatacenterList();
		
		double best = 1000;

		for(int i = 0; i < datacenters.size(); i++) {
			List<EdgeHost> hostlist = datacenters.get(i).getHostList();
			
			for(EdgeHost host : hostlist) {
				Location hostLocation = host.getLocation();
				int xhost = hostLocation.getXPos();
				int yhost = hostLocation.getYPos();
				double dist = Math.sqrt((Math.pow((double)xdev-xhost, 2))+ (Math.pow((double)ydev-yhost, 2)));
				if (dist <= best) {
					best = dist;
					setReceivingBS(datacenters.get(i));
					recBS = i;
					task.setDc(i);
					}
				}
			}
		
		return result;
	}
	
	@Override
	public EdgeVM getVmToOffload(Task task) {
		if(simScenario.equals("TWO_TIER_WITH_EO")) {
			SimLogger.getInstance().setInitialDC(task.getCloudletId(), recBS);
			return selectVmOnLoadBalancer(task);
		}
		else if(simScenario.equals("MECT")) {
			SimLogger.getInstance().setInitialDC(task.getCloudletId(), recBS);
			return selectVmOnMECT(task);
		}
		else if(simScenario.equals("C")) {
			SimLogger.getInstance().setInitialDC(task.getCloudletId(), recBS);
			return selectVmOnC(task);
		}
		else
			return selectVmOnHost(task);
	}
	
	/*
	 * The base case policy;
	 */
	
	public EdgeVM selectVmOnHost(Task task){
		
		EdgeVM selectedVM = getVM(task, recBS);
		
		return selectedVM;
	}
	
	public EdgeVM selectVmOnMECT(Task task){
		int bs = getMectDC(task); 
		finalBS = bs;
		EdgeVM selectedVM = getVM(task, bs);
		return selectedVM;
	}
	
	public EdgeVM selectVmOnC(Task task){
		int bs = getCertainty(task); 
		finalBS = bs;
		EdgeVM selectedVM = getVM(task, bs);
		return selectedVM;
	}
	
	/*
	 * Load Balancer Policy;
	 */
	

	public EdgeVM selectVmOnLoadBalancer(Task task){
	
		int bestBS = getDC(task);
		finalBS = bestBS;
		//System.out.print("The selected BS is" + bestBS + "with probability = " + probab+"\n");
		EdgeVM selectedVM = getVM(task, bestBS);
		return selectedVM;
		
	}
	
	/*
	 * Calculate the deadline for the task
	 * 
	 */
	
	public double deadline(Task task, ETCMatrix b, double slack) {
		
		double comDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(), recBS ,task.getCloudletFileSize()) + 
							SimManager.getInstance().getNetworkModel().getDownloadDelay(recBS, task.getMobileDeviceId(), task.getCloudletFileSize());
		double submissionTime = task.getSubmissionTime();
		double avgMu = 0;
		for(int i = 0; i < b.getDataCnum(); i++) {
			avgMu += b.getMu(i, task.getTaskType().ordinal());
		}
		double avgMuAll = avgMu/b.getDataCnum();
		double deadline =  avgMuAll+ slack + submissionTime + comDelay;
		task.setDeadLine(deadline);
		return deadline;
	}
	
	
	public EdgeVM getVM(Task task, int bs) {
		EdgeVM selectedVM = null;
		List<EdgeVM> vmArray = SimManager.getInstance().edgeServerManager.getDatacenterList().get(bs).getVmList();
		for(int j = 0; j < vmArray.size(); j++) {
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(j).getVmType());
			double targetVmCapacity = (double)100 - vmArray.get(j).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			if(requiredCapacity <= targetVmCapacity) {
				selectedVM = vmArray.get(j);
				
				}
			}
		return selectedVM;
		
	}
	
	
	public int getDC(Task task) {
		
		int bestBS = recBS;
		double dl = SimManager.getInstance().getEdgeOrchestrator().deadline(task, SimLogger.getInstance().matrix, 0.0);
		double bestProb = -1;
		double prob = -1;
		EdgeVM vm = null;
		
		for(int i = 0; i < SimManager.getInstance().getLocalServerManager().getDatacenterList().size(); i++) {
			
			if(i == recBS) {
				prob = SimLogger.getInstance().matrix.getProbability(recBS, task.getTaskType().ordinal(), dl);
				//System.out.print("The receiving BS is" + recBS + "with probability = " + prob + "\n");
			} 
			else {
				double exMu = SimLogger.getInstance().matrix.getMu(i, task.getTaskType().ordinal());
				double exSigma = SimLogger.getInstance().matrix.getSigma(i, task.getTaskType().ordinal());
				double trMu = SimLogger.getInstance().ETTmatrix.getMu(recBS, i);
				double trSigma = SimLogger.getInstance().ETTmatrix.getSigma(recBS, i);
				double finalMu = exMu + trMu;
				double finalSigma = Math.sqrt((exSigma*exSigma) + (trSigma*trSigma));
				NormDistr resultDistr = new NormDistr(finalMu, finalSigma);
			
				prob = SimLogger.getInstance().matrix.getProbConvolved(dl, resultDistr);
				//System.out.print("The compared BS is" + i + "with probability = " + prob + "\n");
			}
			
			vm = getVM(task, i);
			
			if (prob > bestProb) {
				probab = prob;
				bb = i;
				if(vm != null) {
					bestProb = prob;
					bestBS = i;
					}
				}
			}
		if(bestBS < 0) {
			bestBS = bb;
		}
		return bestBS;
	}
	
	public int getMectDC(Task task) {
	
		int mectDC = recBS;
		double compValue = 99999;
		double tmpValue = 0;
		
		List<Datacenter> dcs = SimManager.getInstance().edgeServerManager.getDatacenterList();
		
		for(int i = 0; i < dcs.size(); i++) {
			tmpValue = SimLogger.getInstance().matrix.getMu(i, task.getTaskType().ordinal());
			EdgeVM vm = getVM(task, i);
			if(compValue > tmpValue) {
				if(vm != null) {
					compValue = tmpValue;
					mectDC = i;
				}
			}
		}
		return mectDC;
	}
	
	public int getCertainty(Task task) {
		
		double dl = SimManager.getInstance().getEdgeOrchestrator().deadline(task, SimLogger.getInstance().matrix, 0.0);
		int mectDC = recBS;
		double compValue = -1;
		double tmpValue = 0;
		
		List<Datacenter> dcs = SimManager.getInstance().edgeServerManager.getDatacenterList();
		
		for(int i = 0; i < dcs.size(); i++) {
			tmpValue = dl - SimLogger.getInstance().matrix.getMu(i, task.getTaskType().ordinal());
			EdgeVM vm = getVM(task, i);
			if(compValue < tmpValue) {
				if(vm != null) {
					compValue = tmpValue;
					mectDC = i;
				}
			}
		}
		return mectDC;
	}
	
	/*
	 * Fill the ArrayList for neighboring Base Stations;
	 */
	
	public ArrayList<Integer> getNeighbors() {
		
		ArrayList<Integer> neighboringBS = new ArrayList<>();
		List<EdgeHost> recLoc = receivingBS.getHostList();
		Location recLocation = recLoc.get(0).getLocation();
		int xRec = recLocation.getXPos();
		int yRec = recLocation.getYPos();
		
		List<Datacenter> datacenters = SimManager.getInstance().edgeServerManager.getDatacenterList();

		for(int i = 0; i < datacenters.size(); i++) {
			List<EdgeHost> neighbourlist = datacenters.get(i).getHostList();
			Location neighLocation = neighbourlist.get(0).getLocation();
			int xNeigh = neighLocation.getXPos();
			int yNeigh = neighLocation.getYPos();
			int xdiff = xRec-xNeigh;
			int ydiff = yRec-yNeigh;
			if(Math.abs(xdiff) == 1 && Math.abs(ydiff)== 1) {
				neighboringBS.add(i);		
				}
			}
		neighboringBS.add(recBS);
		return neighboringBS;

	}

	
	

	public Datacenter getReceivingBS() {
		return receivingBS;
	}

	public static void setReceivingBS(Datacenter datacenter) {
		receivingBS = datacenter;
	}
}