package branchAndPrice;

import java.util.List;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.branchingDecisions.BranchingDecision;
import org.jorlib.frameworks.columnGeneration.master.cutGeneration.AbstractInequality;
import columnGeneration.PricingProblem;
import columnGeneration.Route;
import model.EVRPTW;

/**
 * Branching on an "end charging time" arc
 */
public class BranchEndChargingTimeUp  implements BranchingDecision<EVRPTW,Route> {

	public final PricingProblem pricingProblem;				//pricing problem
	public final int coefficient;							//RHS of the branching decision
	public final ChargingTimeInequality inequality;			//inequality
	public List<AbstractInequality> poolOfCuts;				//separated SRCs
	public int timestep;									//timestep associated with the branching decision 

	/**
	 * Constructor of the class
	 * @param pricingProblem associated with this branching decision
	 * @param coefficient of the branching constraint (must be an integer)
	 */
	public BranchEndChargingTimeUp(PricingProblem pricingProblem, int coefficient, List<AbstractInequality> list, int timestep){
		this.pricingProblem=pricingProblem;
		this.coefficient = coefficient;
		this.inequality = new ChargingTimeInequality(coefficient, false, timestep, false);
		this.poolOfCuts = list;
		this.timestep = timestep;
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
		return true;
	}

	/**
	 * Obtains the string of the branching decision
	 */
	@Override
	public String toString(){
		return inequality.toString();
	}
}