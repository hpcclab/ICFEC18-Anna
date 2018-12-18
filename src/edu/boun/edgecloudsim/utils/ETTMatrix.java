package edu.boun.edgecloudsim.utils;

import java.util.HashMap;

import org.apache.commons.math3.distribution.NormalDistribution;


public class ETTMatrix {
	/**
	 *  The matrix is holding a mean and a standard deviation 
	 *  for each type from one base station to another
	 */
	protected NormDistr[][] ettMatrix = null;
	
	/**
	 * The number of DataCenter pairs in the simulation and
	 * the number of Task Types
	 */
	protected int dataCenterNum = 0;
	protected int taskTypeNum = 0;
	
	protected HashMap<String, NormDistr> distributions;

	private String[] arrays;
	
	/**
	 * A private constructor to ensure that only 
	 * an correct initialized matrix could be created
	 */
	
	@SuppressWarnings("unused")
	private ETTMatrix() {
	
	};
	
	/**
	 * A parameterized constructor
	 * @param vmTotalNum takes the total number of VMs in the simulation 
	 * @param cloudletTotalnum takes the total number of Cloudlet Types in the simulation
	 */
	
	public ETTMatrix(int _dataCenterNum, HashMap<String, NormDistr> _distributions) {
		
		this.dataCenterNum = _dataCenterNum;
		this.ettMatrix = new NormDistr[dataCenterNum][dataCenterNum];
		this.distributions = _distributions;
		
		for(String key: distributions.keySet()) {
			
			arrays = key.split("\\.");
			
			int row = Integer.parseInt(arrays[0]);
			int column = Integer.parseInt(arrays[1]);
			NormDistr newDistribution = distributions.get(key);
			this.ettMatrix[row][column] = newDistribution;
			
			
			}
		}
	
	public int getDataCnum() {
		return dataCenterNum;
	}
	

	
	/**
	 * Returns the 
	 * @param dataCenterID
	 * @param cloudletType
	 * @return
	 */
	
	public NormalDistribution getDistribution(int sourceDataCenter, int recDataCenter ) {
			
			if (sourceDataCenter > dataCenterNum || recDataCenter > dataCenterNum) {
				throw new ArrayIndexOutOfBoundsException("The Virtual Machine or the Task Type does not exist in this ETC");
			}
			NormalDistribution newDistr = null;
	
			NormDistr distr = ettMatrix[sourceDataCenter][recDataCenter];
			try {
			newDistr = new NormalDistribution(distr.mean, distr.stdev);
			}
			catch(NullPointerException e) {
				newDistr = new NormalDistribution(0.0, 0.0);
			}
			
			return newDistr;
		}

		/**
		 * Inputs the LognormalDistributions from the HashMap into
		 * the matrix (2d-array)
		 */
	

	public double getMu(int sourceDataCenter, int recDataCenter ) {
		
		if(ettMatrix[sourceDataCenter][recDataCenter] == null) {
			return 0;
		}
		
		NormDistr distr = ettMatrix[sourceDataCenter][recDataCenter];
	
		return distr.mean;
	}
	
	public double getSigma(int sourceDataCenter, int recDataCenter ) {
		
		if(ettMatrix[sourceDataCenter][recDataCenter] == null) {
			return 0;
		}
		
		NormDistr distr = ettMatrix[sourceDataCenter][recDataCenter];
	
		return distr.stdev;
	}
	
	public double getWorseCaseTime(int sourceDataCenter, int recDataCenter ) {
		
		if(ettMatrix[sourceDataCenter][recDataCenter] == null) {
			return 0;
		}
		
		NormDistr distr = ettMatrix[sourceDataCenter][recDataCenter];
		
		return distr.mean+distr.stdev;
		
	}
	
	
	
	public boolean printMatrix() {
		
		for(int i= 0; i < dataCenterNum; i++) {
			for(int j = 0; j < taskTypeNum; j++) {
				NormDistr distr = ettMatrix[i][j];
				if(distr == null) {
					break;
				}
				else {
					System.out.println("Task type " + j + " on DataCenter " + i + " has mean of " + distr.getMean() + " and stdv of "+ distr.getStdev());
				}
			}	
			
		}
		return true;
		
	}


}
