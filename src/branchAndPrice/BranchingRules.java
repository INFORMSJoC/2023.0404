package branchAndPrice;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.AbstractBranchCreator;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.BAPNode;
import org.jorlib.frameworks.columnGeneration.util.MathProgrammingUtil;
import columnGeneration.PricingProblem;
import columnGeneration.Route;
import model.EVRPTW;

/**
 * Class which creates new branches in the Branch-and-Price tree. 
 * The class checks whether a fractional number of vehicles is used
 * The class checks whether there is a fractional arc in the solution 
 * The edge with a fractional value closest to 0.5 is selected for branching.
 * Two important methods are:
 * 	1. canPerformBranching that determines whether the particular branch creator can create the child nodes (there is a fractional arc to branch on)
 *  2. getBranches creates the actual branches
 */

public final class BranchingRules extends AbstractBranchCreator<EVRPTW, Route, PricingProblem>{

	private double vehiclesForBranching=0; 				//number of vehicles used in a solution
	private boolean branchingOnVehicles; 				//true if the branching is on the number of vehicles
	private boolean branchOnCustomerArcs; 				//true if the branching is performed on an arc between customers (or the depot)
	private boolean branchOnInitialChargingTime;		//true if the branching is performed on an arc representing the initial charging time
	private int arcForBranching=-1; 					//arc to branch on
	private double bestArcValue = 0; 					//current flow value of the arc to branch on
	private int timestepForBranching=-1; 				//timestep to branch on
	private double bestTimestepValue = 0; 				//number of vehicles charging of the timestep to branch on
	private EVRPTW dataModel; 							//model data

	public BranchingRules(EVRPTW dataModel, PricingProblem pricingProblem){
		super(dataModel, pricingProblem);
		this.dataModel = dataModel;
	}

	/**
	 * Determine the next branching decision
	 * It can be the number of vehicles used
	 * Or if that is an integer number then a fractional arc.
	 * @param solution Fractional column generation solution
	 * @return true if a fractional number of vehicles is used or a fractional arc exists
	 */
	@Override
	protected boolean canPerformBranching(List<Route> solution) {

		//Reset values
		this.vehiclesForBranching = 0;
		this.branchingOnVehicles = false;
		this.branchOnCustomerArcs = false;
		this.branchOnInitialChargingTime = false;
		this.arcForBranching = -1;
		this.bestArcValue = 0;
		this.timestepForBranching = -1;
		this.bestTimestepValue = 0;

		//Aggregate route values
		for(Route route : solution){vehiclesForBranching+=route.value;}
		if(MathProgrammingUtil.isFractional(vehiclesForBranching)) {branchingOnVehicles = true; return true;}

		//Determine whether there's a fractional edge for branching
		Map<Integer, Double> arcValues=new LinkedHashMap<>();

		//Aggregate edge values
		for(Route route : solution){
			if(route.value<1) {
				for(int arc : route.arcs){
					Double arcValue=arcValues.get(arc);
					if(arcValue == null) arcValues.put(arc,route.value);
					else arcValues.put(arc,route.value+arcValue);
				}
			}
		}

		//Select the edge with a fractional value closest to 0.5
		for(int arc : arcValues.keySet()){
			double value=arcValues.get(arc);
			if(Math.abs(0.5-value) <= Math.abs(0.5- bestArcValue)){
				arcForBranching=arc;
				bestArcValue =value;
				if(bestArcValue == 0.5 && dataModel.arcs[arc].tail!= 0 && dataModel.arcs[arc].head!=dataModel.C+1) {branchOnCustomerArcs = true; return true;}
			}
		}
		if(MathProgrammingUtil.isFractional(bestArcValue)) {branchOnCustomerArcs = true; return true;}

		//End charging time
		for (int r = 0; r < solution.size(); r++) {
			Route route1 = solution.get(r);
			int t = route1.initialChargingTime + route1.chargingTime-1;
			double flow = route1.value;
			for (int r2 = r+1; r2 < solution.size(); r2++) {
				Route route2 = solution.get(r2);
				if(route2.initialChargingTime + route2.chargingTime-1==t)
					flow+=route2.value;
			}
			if(MathProgrammingUtil.isFractional(flow)) {
				branchOnInitialChargingTime = false;
				timestepForBranching = t;
				bestTimestepValue = flow;
				return true;
			}
		}

		//Initial charging time
		for (int r = 0; r < solution.size(); r++) {
			Route route1 = solution.get(r);
			int t = route1.initialChargingTime;
			double flow = route1.value;
			for (int r2 = r+1; r2 < solution.size(); r2++) {
				Route route2 = solution.get(r2);
				if(route2.initialChargingTime==t)
					flow+=route2.value;
			}
			if(MathProgrammingUtil.isFractional(flow)) {
				branchOnInitialChargingTime = true;
				timestepForBranching = t;
				bestTimestepValue = flow;
				return true;
			}
		}
		return false;
	}

	/**
	 * Create the branches:
	 * branch 1: edge {@code edgeForBranching} must be used by {@code PricingProblem},</li>
	 * 	branch 2: edge {@code edgeForBranching} may NOT used by {@code PricingProblem},</li>
	 * @param parentNode Fractional node on which we branch
	 * @return List of child nodes
	 */
	@Override
	protected List<BAPNode<EVRPTW,Route>> getBranches(BAPNode<EVRPTW,Route> parentNode) {
		BAPNode<EVRPTW,Route> node2; 		//one child node
		BAPNode<EVRPTW,Route> node1; 		//other child node

		if(branchingOnVehicles) {
			//Branch 1: number of vehicles down
			BranchVehiclesDown branchingDecision1=new BranchVehiclesDown(this.pricingProblems.get(0), (int) Math.floor(vehiclesForBranching), parentNode.getInequalities());
			node1=this.createBranch(parentNode, branchingDecision1, parentNode.getInitialColumns(), parentNode.getInequalities());
			//Branch 2: number of vehicles up
			BranchVehiclesUp branchingDecision2=new BranchVehiclesUp(this.pricingProblems.get(0), (int) Math.ceil(vehiclesForBranching), parentNode.getInequalities());
			node2=this.createBranch(parentNode, branchingDecision2, parentNode.getInitialColumns(), parentNode.getInequalities());
		}else if(branchOnCustomerArcs){
			//Branch 1: remove the edge:
			RemoveArc branchingDecision1=new RemoveArc(this.pricingProblems.get(0), arcForBranching, dataModel, parentNode.getInequalities(), bestArcValue);
			node2=this.createBranch(parentNode, branchingDecision1, parentNode.getInitialColumns(), parentNode.getInequalities());
			//Branch 2: fix the edge:
			FixArc branchingDecision2=new FixArc(this.pricingProblems.get(0), arcForBranching, dataModel, parentNode.getInequalities(), bestArcValue);
			node1=this.createBranch(parentNode, branchingDecision2, parentNode.getInitialColumns(), parentNode.getInequalities());
		}else {
			if(branchOnInitialChargingTime) {
				//Branch 1: remove the edge:
				BranchInitialChargingTimeDown branchingDecision1= new BranchInitialChargingTimeDown(this.pricingProblems.get(0), (int) Math.floor(bestTimestepValue),parentNode.getInequalities(), this.timestepForBranching);
				node2=this.createBranch(parentNode, branchingDecision1, parentNode.getInitialColumns(), parentNode.getInequalities());
				//Branch 2: fix the edge:
				BranchInitialChargingTimeUp branchingDecision2=new BranchInitialChargingTimeUp(this.pricingProblems.get(0), (int) Math.ceil(bestTimestepValue),parentNode.getInequalities(), this.timestepForBranching);
				node1=this.createBranch(parentNode, branchingDecision2, parentNode.getInitialColumns(), parentNode.getInequalities());
			}else {
				//Branch 1: remove the edge:
				BranchEndChargingTimeDown branchingDecision1= new BranchEndChargingTimeDown(this.pricingProblems.get(0), (int) Math.floor(bestTimestepValue),parentNode.getInequalities(), this.timestepForBranching);
				node2=this.createBranch(parentNode, branchingDecision1, parentNode.getInitialColumns(), parentNode.getInequalities());
				//Branch 2: fix the edge:
				BranchEndChargingTimeUp branchingDecision2=new BranchEndChargingTimeUp(this.pricingProblems.get(0), (int) Math.ceil(bestTimestepValue),parentNode.getInequalities(), this.timestepForBranching);
				node1=this.createBranch(parentNode, branchingDecision2, parentNode.getInitialColumns(), parentNode.getInequalities());
			}
		}
		return Arrays.asList(node1,node2);
	}
}