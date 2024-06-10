package branchAndPrice;

import java.util.ArrayList;
import java.util.List;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.branchingDecisions.BranchingDecision;
import org.jorlib.frameworks.columnGeneration.master.cutGeneration.AbstractInequality;
import columnGeneration.PricingProblem;
import columnGeneration.Route;
import model.EVRPTW;
import model.EVRPTW.Arc;


/**
 * Ensure that an arc is used (Branching on the arc-flow variables >=)
 */
public final class FixArc implements BranchingDecision<EVRPTW,Route> {


	public final PricingProblem pricingProblem;				//pricing problem
	public final int arc;									//arc on which we branch
	public double flowValue;								//flow value of the arc on which we are branching
	public List<AbstractInequality> poolOfCuts;				//separated SRCs
	public EVRPTW dataModel;								//data model
	public ArrayList<Integer> infeasibleArcs;				//infeasible arcs by the branching decision


	public FixArc(PricingProblem pricingProblem, int arc, EVRPTW dataModel, List<AbstractInequality> list, double flowValue){
		this.pricingProblem=pricingProblem;
		this.arc=arc;
		this.dataModel = dataModel;
		this.poolOfCuts = list;
		this.infeasibleArcs = new ArrayList<Integer>();
		this.flowValue = flowValue;

		int tail = dataModel.arcs[arc].tail;
		int head = dataModel.arcs[arc].head;
		if(tail>0) { //i is a customer
			for(Arc otherArc: dataModel.graph.outgoingEdgesOf(tail)) 
				if(otherArc.id!=arc && !dataModel.infeasibleArcs[otherArc.id]) infeasibleArcs.add(otherArc.id);
		}
		if(head<dataModel.C+1) { //j is a customer
			for(Arc otherArc: dataModel.graph.incomingEdgesOf(head)) 
				if(otherArc.id!=arc && !dataModel.infeasibleArcs[otherArc.id]) infeasibleArcs.add(otherArc.id);
		}
	}

	/**
	 * Determine whether the given inequality remains feasible for the child node
	 * @param inequality inequality
	 * @return true
	 */
	@Override
	public boolean inEqualityIsCompatibleWithBranchingDecision(AbstractInequality inequality) {
		return true;
	}

	/**
	 * Determine whether the given column remains feasible for the child node
	 * @param column column
	 * @return true if the column is compliant with the branching decision
	 */
	@Override
	public boolean columnIsCompatibleWithBranchingDecision(Route column) {
		if(column.associatedPricingProblem != this.pricingProblem) return false;
		if(column.isArtificialColumn) return true;
		int tail = dataModel.arcs[arc].tail; 
		int head = dataModel.arcs[arc].head;

		if(tail>0 && head<dataModel.C+1) { //both are customers
			if(!column.route.containsKey(tail) && !column.route.containsKey(head-1)) return true; //does not visits i and j
		}

		//infeasible arcs
		for(int edge: infeasibleArcs) if(column.arcs.contains(edge)) return false;

		return true;
	}

	@Override
	public String toString(){
		return "Fix: "+ dataModel.arcs[arc].toString() + " Current flow-value: " + this.flowValue;
	}
}