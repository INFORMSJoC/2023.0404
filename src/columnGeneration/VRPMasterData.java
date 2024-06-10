package columnGeneration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jorlib.frameworks.columnGeneration.master.MasterData;
import org.jorlib.frameworks.columnGeneration.util.OrderedBiMap;

import branchAndPrice.ChargingTimeInequality;
import branchAndPrice.NumberVehiclesInequalities;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import model.EVRPTW;

/**
 * Class that stores the information for the Master Problem (MP).
 */
public final class VRPMasterData extends MasterData<EVRPTW, Route, PricingProblem, IloNumVar>{

	public final IloCplex cplex;												//CPLEX instance
	public final PricingProblem pricingProblem;									//list of pricing problems
	public HashMap<SubsetRowInequality, IloRange> subsetRowInequalities;		//mapping of the Subset row inequalities to constraints in the CPLEX model
	public Map<NumberVehiclesInequalities, IloRange> branchingNumberOfVehicles;	//mapping of branching decisions on the number of vehicles
	public Map<ChargingTimeInequality, IloRange> branchingChargingTimes;		//mapping of branching decisions on the charging times
	public Map<Route, Double> routeValueMap;									//routes used (only non-zero routes are considered) 

	public VRPMasterData(IloCplex cplex, PricingProblem pricingProblem, Map<PricingProblem, OrderedBiMap<Route, IloNumVar>> varMap) {
		super(varMap);
		this.cplex = cplex;
		this.pricingProblem = pricingProblem;
		this.subsetRowInequalities = new LinkedHashMap<>();
		this.routeValueMap = new HashMap<>();
		this.branchingNumberOfVehicles = new HashMap<NumberVehiclesInequalities, IloRange>();
		this.branchingChargingTimes = new HashMap<ChargingTimeInequality, IloRange>();
	}
}