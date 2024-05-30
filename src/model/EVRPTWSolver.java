package model;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.AbstractBranchAndPrice;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.AbstractBranchCreator;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.BAPNode;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.EventHandling.BranchEvent;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.EventHandling.FinishEvent;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.EventHandling.FinishMasterEvent;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.EventHandling.FinishPricingEvent;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.EventHandling.ProcessingNextNodeEvent;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.EventHandling.PruneNodeEvent;
import org.jorlib.frameworks.columnGeneration.colgenMain.AbstractColumn;
import org.jorlib.frameworks.columnGeneration.io.SimpleDebugger;
import org.jorlib.frameworks.columnGeneration.master.cutGeneration.CutHandler;
import org.jorlib.frameworks.columnGeneration.pricing.AbstractPricingProblemSolver;
import org.jorlib.frameworks.columnGeneration.util.Configuration;
import branchAndPrice.BranchAndPrice;
import branchAndPrice.BranchingRules;
import columnGeneration.ExactLabelingMultigraphPricingProblemSolver;
import columnGeneration.HeuristicMinCostLabelingPricingProblemSolver;
import columnGeneration.HeuristicLabelingMultigraphPricingProblemSolver;
import columnGeneration.HeuristicLabelingPricingProblemSolver;
import columnGeneration.Master;
import columnGeneration.PricingProblem;
import columnGeneration.Route;
import columnGeneration.SubsetRowInequalityGenerator;
import columnGeneration.VRPMasterData;
import model.EVRPTW.Arc;

/**
 * Solver class for the mE-VRSPTW (BPC algorithm).
 */
public final class EVRPTWSolver {

	private final EVRPTW dataModel;  		//information about the instance
	private int upperBound; 				//upper bound on column generation solution (stronger is better).

	public EVRPTWSolver(EVRPTW dataModel) throws FileNotFoundException{

		this.dataModel = dataModel;

		//Properties
		Properties properties = new Properties();
		properties.setProperty("MAXTHREADS", "1"); //only one thread
		Configuration.readFromFile(properties);

		//Create a cutHandler, then create a SRC AbstractInequality Generator and add it to the handler
		CutHandler<EVRPTW, VRPMasterData> cutHandler=new CutHandler<>();
		SubsetRowInequalityGenerator cutGen = new SubsetRowInequalityGenerator(dataModel);
		cutHandler.addCutGenerator(cutGen);

		//Create the pricing problem
		PricingProblem pricingProblem = new PricingProblem(dataModel, "EVRSPTWPricing");

		//Create the master problem
		Master master=new Master(dataModel, pricingProblem, cutHandler);

		//Define which solvers to use (one or more)
		List<Class<? extends AbstractPricingProblemSolver<EVRPTW, Route, PricingProblem>>> solvers= new ArrayList<>();
		solvers.add(HeuristicLabelingPricingProblemSolver.class);
		solvers.add(HeuristicMinCostLabelingPricingProblemSolver.class);
		solvers.add(HeuristicLabelingMultigraphPricingProblemSolver.class);
		solvers.add(ExactLabelingMultigraphPricingProblemSolver.class);
		
		//Create a set of initial columns and use it as an upper bound
		List<Route> initSolution=this.getInitialSolution(pricingProblem);

		//Define Branch creators
		List<? extends AbstractBranchCreator<EVRPTW, Route, PricingProblem>> branchCreators= Collections.singletonList(new BranchingRules(dataModel, pricingProblem));

		//Create a Branch-and-Price instance
		BranchAndPrice bap=new BranchAndPrice(dataModel, master, pricingProblem, solvers, branchCreators, upperBound, initSolution);

		//OPTIONAL: Attach a debugger
		SimpleDebugger debugger=new PersonalizedDebbuger(bap, cutHandler, true);

		//Solve the problem problem through Branch-and-Price
		bap.runBranchAndPrice(System.currentTimeMillis()+7200000L);

		//Print solution
		PrintWriter out;
		try {
			out = new PrintWriter(new BufferedWriter(new FileWriter("./results/output.txt", true)));
			out.print(dataModel.getName()+"\t"+bap.getSolution().size()+"\t"+	getScaledObjective(bap.getBoundRootNode())+"\t"+ dataModel.columnsRootNode + "\t"+ dataModel.cutsRootNode+ "\t"
					+bap.getNumberOfProcessedNodes() +"\t" + getTimeInSeconds(bap.getMasterSolveTime())+"\t"+getTimeInSeconds(bap.getPricingSolveTime())+"\t"+getTimeInSeconds(bap.getSolveTime()) +"\t"+ getScaledObjective(bap.getObjective())
					+ "\t");

			double[] chargingInformation = getChargingInformation(bap.getSolution());
			out.print(dataModel.B +  "\t" + chargingInformation[0] + "\t"+ chargingInformation[1] + "\t"+ chargingInformation[2]);
			out.print("\n");
			out.close();

		} catch (IOException e) {
			// Do nothing
		}

		//Clean up:
		bap.close(); 		//close master and pricing problems
		cutHandler.close(); //close the cut handler. The close() call is propagated to all registered AbstractCutGenerator classes
	}

	/** Computes the charging schedule statistics for a given solution. */
	public double[] getChargingInformation(List<Route> solution) {

		//Min and max charging start and end time
		int minChargingTime = dataModel.last_charging_period;
		int maxChargingTime = 1;
		for(Route route:solution) {
			if(route.initialChargingTime<minChargingTime) minChargingTime = route.initialChargingTime;
			if(route.initialChargingTime+route.chargingTime-1>maxChargingTime) maxChargingTime = route.initialChargingTime+route.chargingTime-1;
		}


		//number of vehicles charging each timestep
		int[] vehiclesCharging = new int[maxChargingTime+1];
		for (int t = minChargingTime; t <= maxChargingTime; t++) {
			for(Route route: solution) {
				int charge = (route.initialChargingTime<=t && t<=route.initialChargingTime+route.chargingTime-1) ? 1 : 0;
				vehiclesCharging[t]+= charge;
			}
		}

		//number of timesteps chargers are in use and avg. number of vehicles charging
		double numberOfTimesInUse = 0;
		double totalVehiclesCharging = 0;
		double timestepsAtFullCapacity = 0;
		for (int t = minChargingTime; t <= maxChargingTime; t++) {
			if(vehiclesCharging[t]>0) {
				numberOfTimesInUse++;
				totalVehiclesCharging+=vehiclesCharging[t];
				if(vehiclesCharging[t]==dataModel.B) timestepsAtFullCapacity++;
			}
		}

		double averageTimeInUse = Math.floor(numberOfTimesInUse/(maxChargingTime-minChargingTime+1)*10000)/10000;
		double averageVehiclesCharging = totalVehiclesCharging/(maxChargingTime-minChargingTime+1);
		averageVehiclesCharging = Math.floor(averageVehiclesCharging/dataModel.B*10000)/10000;
		double averageTimeAtFullCapacity = timestepsAtFullCapacity/(maxChargingTime-minChargingTime+1);
		averageTimeAtFullCapacity = Math.floor(averageTimeAtFullCapacity*10000)/10000;
		return new double[] {averageTimeInUse, averageVehiclesCharging, averageTimeAtFullCapacity};
	}

	/**
	 * Create an initial solution for the mE-VRSPTW.
	 * Simple initial solution: visit each customer with a single vehicle and assign a charging schedule. 
	 * @return initial set of routes
	 */
	private List<Route> getInitialSolution(PricingProblem pricingProblem){

		List<Route> initSolution = new ArrayList<>();

		//Dummy (artificial) routes to identify infeasibility and initialize the CG
		HashMap<Integer, Integer> route=new HashMap<Integer, Integer>(dataModel.C);
		int[] routeSequence = new int[dataModel.C];
		for(int i=0; i< dataModel.C; i++) {route.put(i+1, 1); routeSequence[i] = i+1;}
		upperBound = (int) Math.pow(10, 20);
		initSolution.add(new Route("initSolution", true, route, routeSequence, pricingProblem, (int) Math.pow(10, 20), 0, 0, 0, 0.0, new ArrayList<Integer>(), 0, 0)); //dummy 

		//Dummy routes (possibly feasible)
		for(int i=1; i<=dataModel.C; i++){ //a route for each customer
			route = new HashMap<Integer, Integer>();
			route.put(i, 1);
			boolean added = false;
			for(Arc arc: dataModel.graph.getAllEdges(0, i)) {
				if(added) break;
				for(Arc arc2: dataModel.graph.getAllEdges(i, dataModel.C+1)) {
					int cost = arc.cost+arc2.cost;
					int energy = arc.energy+arc2.energy;
					if(energy>dataModel.E) continue;
					routeSequence = new int[] {i};
					ArrayList<Integer> arcs = new ArrayList<Integer>(dataModel.C);
					arcs.add(arc.id);arcs.add(arc2.id);
					int latestDeparture = dataModel.vertices[i].closing_tw-arc.time;
					latestDeparture = (int) (latestDeparture/10);
					int initialChargingTime = latestDeparture-dataModel.f_inverse[energy];

					//Add the route
					Route column=new Route("initSolution", false, route, routeSequence, pricingProblem, cost, latestDeparture, energy, dataModel.vertices[i].load, 0.0, arcs, initialChargingTime, dataModel.f_inverse[energy]);
					column.BBnode=0;
					initSolution.add(column);
					added = true;
					break;
				}
			}
		}

		return initSolution;
	}

	/** Main class. Here the program starts.
	 *  The args[0] must be the instance name. 
	 *  The instance file needs to be in ./data
	 * */
	public static void main(String[] args) throws IOException{
		EVRPTW evrptw=new EVRPTW(args[0]);
		new EVRPTWSolver(evrptw);
	}

	/** Returns the real (double) objective (divided by 10). */
	public double getScaledObjective(double objective) {
		double realCost = objective;
		realCost = realCost*0.1+0.05;
		return Math.floor(realCost*10)/10;
	}

	/** Returns the time in seconds (and considering two decimals). */
	public double getTimeInSeconds(double time) {
		double realTime = time*0.001;
		realTime = Math.floor(realTime*100)/100; //two decimals
		return realTime;
	}


	/** Defines a personalized debbuger (for the log files). */
	public class PersonalizedDebbuger extends SimpleDebugger{

		public PersonalizedDebbuger(AbstractBranchAndPrice bap, CutHandler cutHanlder, boolean captureColumnGenerationEventsBAP) {
			super(bap, cutHanlder, true);
		}
		@Override
		public void finishPricing(FinishPricingEvent finishPricingEvent) {
			String solver = (finishPricingEvent.columns.size()==0) ? "exactLabeling": finishPricingEvent.columns.get(0).creator;
			logger.debug("Finished pricing ({}, {} columns generated) -> CG objective: {}, CG bound: {}, CG cutoff: {}", new Object[]{solver, finishPricingEvent.columns.size(), finishPricingEvent.objective, finishPricingEvent.boundOnMasterObjective, finishPricingEvent.cutoffValue});
			for(AbstractColumn<?, ?> column : finishPricingEvent.columns){
				logger.debug(column.toString());
			}
		}
		@Override
		public void finishMaster(FinishMasterEvent finishMasterEvent) {
			logger.debug("Finished master -> CG objective: {}, CG bound: {}, CG cutoff: {}", new Object[]{finishMasterEvent.objective, finishMasterEvent.boundOnMasterObjective, finishMasterEvent.cutoffValue});
			logger.debug("Total running time (s): " + getTimeInSeconds(System.currentTimeMillis()-bap.getSolveTime()));
		}
		@Override
		public void branchCreated(BranchEvent branchEvent) {
			logger.debug("================ BRANCHING ================");
			logger.debug("Branching - {} new nodes: ",branchEvent.nrBranches);
			for(BAPNode childNode : branchEvent.childNodes){
				logger.debug("ChildNode {} - {}",childNode.nodeID, childNode.getBranchingDecision().toString());
			}
		}
		@Override
		public void processNextNode(ProcessingNextNodeEvent processingNextNodeEvent) {
			logger.debug("================ PROCESSING NODE {} ================", processingNextNodeEvent.node.nodeID);
			logger.debug("Nodes remaining in queue: {} - Node bound: {} - Incumbent solution: {}",new Object[]{processingNextNodeEvent.nodesInQueue, processingNextNodeEvent.node.getBound(), processingNextNodeEvent.objectiveIncumbentSolution});
		}
		@Override
		public void pruneNode(PruneNodeEvent pruneNodeEvent) {
			logger.debug("Pruning node {}. Bound: {}, best integer solution: {}", new Object[]{pruneNodeEvent.node.nodeID, pruneNodeEvent.nodeBound, pruneNodeEvent.bestIntegerSolution});
		}
		@Override
		public void finishBAP(FinishEvent finishEvent) {
			logger.debug("================ SOLUTION BPC - " + instanceName +" ================");
			logger.debug("BAP terminated with objective: "+getScaledObjective(bap.getObjective()));
			logger.debug("Total Number of iterations: "+bap.getTotalNrIterations());
			logger.debug("Total Number of processed nodes: "+bap.getNumberOfProcessedNodes());
			logger.debug("Total Time spent on master problems (s): "+getTimeInSeconds(bap.getMasterSolveTime())+" Total time spent on pricing problems (s): "+getTimeInSeconds(bap.getPricingSolveTime()));
			logger.debug("Total running time (s): "+ getTimeInSeconds(System.currentTimeMillis()-bap.getSolveTime()));
			if(bap.hasSolution()) {
				logger.debug("Solution is optimal: "+bap.isOptimal());
				logger.debug("Columns (only non-zero columns are returned):");
				List<Route> solution = bap.getSolution();
				for (Route column : solution)
					logger.debug(column.toString());
			}
		}
	}
}