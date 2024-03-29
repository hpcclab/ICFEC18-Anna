/*
 * Title:        EdgeCloudSim - Mobile Device Manager
 * 
 * Description: 
 * MobileDeviceManager is responsible for submitting the tasks to the related
 * device by using the Edge Orchestrator. It also takes proper actions 
 * when the execution of the tasks are finished.
 * By default, MobileDeviceManager sends tasks to the edge servers or
 * cloud servers. If you want to use different topology, for example
 * MAN edge server, you should modify the flow defined in this class.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.lists.VmList;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.EdgeTask;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.edge_orchestrator.BasicEdgeOrchestrator;

public class MobileDeviceManager extends DatacenterBroker {
	private static final int BASE = 100000; //start from base in order not to conflict cloudsim tag!
	private static final int REQUEST_RECEIVED_BY_CLOUD = BASE + 1;
	private static final int REQUEST_PROCESSED_BY_CLOUD = BASE + 2;
	private static final int REQUEST_RECIVED_BY_EDGE_DEVICE = BASE + 3;
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 4;
	private static final int REQUEST_RECIVED_BY_EDGE_ORCHESTRATOR = BASE + 5;
	private int taskIdCounter=0;
	
	public MobileDeviceManager() throws Exception {
		super("Global_Broker");
	}

	/**
	 * Submit cloudlets to the created VMs.
	 * 
	 * @pre $none
	 * @post $none
	 */
	protected void submitCloudlets() {

	}
	
	/**
	 * Process a cloudlet return event.
	 * 
	 * @param ev a SimEvent object
	 * @pre ev != $null
	 * @post $none
	 */
	protected void processCloudletReturn(SimEvent ev) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();

		Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock());
		//if(task.getSubmittedLocation().equals(currentLocation))
		//{
			//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + task.getCloudletId() + " received");
			double WlanDelay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task.getCloudletOutputSize());
			if(WlanDelay > 0)
			{
				networkModel.downloadStarted(currentLocation, SimSettings.GENERIC_EDGE_DEVICE_ID);
				schedule(getId(), WlanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				SimLogger.getInstance().downloadStarted(task.getCloudletId(), WlanDelay);
			}
			else
			{
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock());
			}
		}
		//else
		//{
			//SimLogger.printLine("task cannot be finished due to mobility of user!");
			//SimLogger.printLine("device: " +task.getMobileDeviceId()+" - submitted " + task.getSubmissionTime() + " @ " + task.getSubmittedLocation().getXPos() + " handled " + CloudSim.clock() + " @ " + currentLocation.getXPos());
		//	SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
		//}
	//}
	
	protected void processOtherEvent(SimEvent ev) {
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(0);
			return;
		}
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		switch (ev.getTag()) {
			case REQUEST_RECEIVED_BY_CLOUD:
			{
				Task task = (Task) ev.getData();
				
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				
				//save related host id
				task.setAssociatedHostId(SimSettings.CLOUD_HOST_ID);
				
				SimLogger.getInstance().uploaded(task.getCloudletId(),
						SimSettings.CLOUD_DATACENTER_ID,
						SimSettings.CLOUD_HOST_ID,
						SimSettings.CLOUD_VM_ID,
						SimSettings.VM_TYPES.CLOUD_VM.ordinal(),  CloudSim.clock());
				
				//calculate computational delay in cloud
				double ComputationDelay = (double)task.getCloudletLength() / (double)SimSettings.getInstance().getMipsForCloud();
				
				schedule(getId(), ComputationDelay, REQUEST_PROCESSED_BY_CLOUD, task);
				
				break;
			}
			case REQUEST_PROCESSED_BY_CLOUD:
			{
				Task task = (Task) ev.getData();

				//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + task.getCloudletId() + " received");
				double WanDelay = networkModel.getDownloadDelay(SimSettings.CLOUD_DATACENTER_ID, task.getMobileDeviceId(), task.getCloudletOutputSize());
				if(WanDelay > 0)
				{
					Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+WanDelay);
					if(task.getSubmittedLocation().equals(currentLocation))
					{
						networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
						schedule(getId(), WanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
						SimLogger.getInstance().downloadStarted(task.getCloudletId(), WanDelay);
					}
					else
					{
						//SimLogger.printLine("task cannot be finished due to mobility of user!");
						//SimLogger.printLine("device: " +task.getMobileDeviceId()+" - submitted " + task.getSubmissionTime() + " @ " + task.getSubmittedLocation().getXPos() + " handled " + CloudSim.clock() + " @ " + currentLocation.getXPos());
						SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
					}
				}
				else
				{
					SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock());
				}
				break;
			}
			
			case REQUEST_RECIVED_BY_EDGE_ORCHESTRATOR:
			{
				Task task = (Task) ev.getData();
				if(BasicEdgeOrchestrator.getFinalBS() == BasicEdgeOrchestrator.getRecBS()) {
					networkModel.downloadStarted(task.getSubmittedLocation(), task.getMobileDeviceId());
					SimLogger.getInstance().transferStarted(task.getCloudletId(), 0.0);

					submitTaskToEdgeDevice(task,0.0);
				
				}
				
				else {
					
					double internalDelay = networkModel.getDownloadDelay(
							SimSettings.EDGE_ORCHESTRATOR_ID,
							SimSettings.GENERIC_EDGE_DEVICE_ID,
							task.getCloudletOutputSize());
							
					networkModel.downloadStarted(task.getSubmittedLocation(), task.getMobileDeviceId());
					SimLogger.getInstance().transferStarted(task.getCloudletId(), internalDelay);

					submitTaskToEdgeDevice(task,internalDelay);
					
				}
				
				break;
			}
			
			case REQUEST_RECIVED_BY_EDGE_DEVICE:
			{
				Task task = (Task) ev.getData();
				
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				SimLogger.getInstance().transferStarted(task.getCloudletId(), 0.0);
				
				submitTaskToEdgeDevice(task,0);
				
				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
			{
				Task task = (Task) ev.getData();
				
				if(task.getAssociatedHostId() == SimSettings.CLOUD_HOST_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				else
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				
				//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + cloudlet.getCloudletId() + " is received");
				SimLogger.getInstance().downloaded(task.getCloudletId(), CloudSim.clock());
				break;
			}

			default:
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(0);
				break;
		}
	}
	
	public void submitTaskToEdgeDevice(Task task, double delay) {
		//select a VM
		EdgeVM selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task);
		
		if(selectedVM != null){
			//save related host id
			task.setAssociatedHostId(selectedVM.getHost().getId());
			
			
			//bind task to related VM
			getCloudletList().add(task);
			bindCloudletToVm(task.getCloudletId(),selectedVM.getId());
			
			//SimLogger.printLine(CloudSim.clock() + ": Cloudlet#" + task.getCloudletId() + " is submitted to VM#" + task.getVmId());
			schedule(getVmsToDatacentersMap().get(task.getVmId()), delay, CloudSimTags.CLOUDLET_SUBMIT, task);
			
			SimLogger.getInstance().uploaded(task.getCloudletId(),
					selectedVM.getHost().getDatacenter().getId(),
					selectedVM.getHost().getId(),
					selectedVM.getId(),
					selectedVM.getVmType().ordinal(),  CloudSim.clock());
		}
		else{
			//SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
			SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), task.getDc());
			
		}
	}
	
	public void submitTask(EdgeTask edgeTask) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		
		//create a task
		Task task = createTask(edgeTask);
		
		Location currentLocation = SimManager.getInstance().getMobilityModel().
				getLocation(task.getMobileDeviceId(),CloudSim.clock());
		
		//set location of the mobile device which generates this task
		task.setSubmittedLocation(currentLocation);


		int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task);
		
		SimLogger.getInstance().addLog(CloudSim.clock(),
				task.getCloudletId(),
				task.getTaskType().ordinal(),
				(int)task.getCloudletLength(),
				(int)task.getCloudletFileSize(),
				(int)task.getCloudletOutputSize(), nextHopId);
		
		if(nextHopId == SimSettings.CLOUD_DATACENTER_ID){
			double WanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task.getCloudletFileSize());
			
			if(WanDelay>0){
				networkModel.uploadStarted(currentLocation, nextHopId);
				schedule(getId(), WanDelay, REQUEST_RECEIVED_BY_CLOUD, task);
				SimLogger.getInstance().uploadStarted(task.getCloudletId(),WanDelay, CloudSim.clock());
			}
			else
			{
				//SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.CLOUD_VM.ordinal());
			}
		}
		else if(nextHopId == SimSettings.EDGE_ORCHESTRATOR_ID){
			double WlanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), SimSettings.GENERIC_EDGE_DEVICE_ID, task.getCloudletFileSize());
			
			if(WlanDelay > 0){
				networkModel.uploadStarted(currentLocation, nextHopId);
				schedule(getId(), WlanDelay, REQUEST_RECIVED_BY_EDGE_ORCHESTRATOR, task);
				SimLogger.getInstance().uploadStarted(task.getCloudletId(),WlanDelay, CloudSim.clock());
			}
			else {
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.EDGE_VM.ordinal());
			}
		}
		else if(nextHopId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
			double WlanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task.getCloudletFileSize());
			
			if(WlanDelay > 0){
				networkModel.uploadStarted(currentLocation, nextHopId);
				schedule(getId(), WlanDelay, REQUEST_RECIVED_BY_EDGE_DEVICE, task);
				SimLogger.getInstance().uploadStarted(task.getCloudletId(),WlanDelay, CloudSim.clock());
			}
			else {
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.EDGE_VM.ordinal());
			}
		}
		else {
			SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
			System.exit(0);
		}
		
		//add related task to the logger 
		
		
	}
	
	public Task createTask(EdgeTask edgeTask){
		UtilizationModel utilizationModel = new UtilizationModelFull(); /*UtilizationModelStochastic*/
		UtilizationModel utilizationModelCPU = SimManager.getInstance().getScenarioFactory().getCpuUtilizationModel(edgeTask.taskType);

		Task task = new Task(edgeTask.mobileDeviceId, ++taskIdCounter,
				edgeTask.length, edgeTask.pesNumber,
				edgeTask.inputFileSize, edgeTask.outputFileSize,
				utilizationModelCPU, utilizationModel, utilizationModel);
		
		//set the owner of this task
		task.setUserId(this.getId());
		task.setTaskType(edgeTask.taskType);
		
		return task;
	}

	public void taskEnded(){
		clearDatacenters();
		finishExecution();
	}
}
