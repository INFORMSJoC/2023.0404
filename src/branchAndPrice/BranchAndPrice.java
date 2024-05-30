package branchAndPrice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.AbstractBranchAndPrice;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.AbstractBranchCreator;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.BAPNode;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.EventHandling.CGListener;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.bapNodeComparators.BFSbapNodeComparator;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.bapNodeComparators.DFSbapNodeComparator;
import org.jorlib.frameworks.columnGeneration.io.TimeLimitExceededException;
import org.jorlib.frameworks.columnGeneration.pricing.AbstractPricingProblemSolver;
import columnGeneration.Master;
import columnGeneration.PricingProblem;
import columnGeneration.Route;
import columnGeneration.customCG;
import ilog.concert.IloColumn;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import model.EVRPTW;

/**
 * Branch-and-Price class
 */
public final class BranchAndPrice extends AbstractBranchAndPrice<EVRPTW,Route,PricingProblem> {

	PricingProblem pricingProblem; 					//pricing problem
	public static final double PRECISION=0.001; 	//precision considered for the fractional solutions (nodes)

	public BranchAndPrice(EVRPTW modelData,
			Master master,
			PricingProblem pricingProblem,
			List<Class<? extends AbstractPricingProblemSolver<EVRPTW,Route,PricingProblem>>> solvers,
			List<? extends AbstractBranchCreator<EVRPTW,Route,PricingProblem>> branchCreators,
					int objectiveInitialSolution,
					List<Route> initialSolution){
		super(modelData, master, pricingProblem, solvers, branchCreators, 0, objectiveInitialSolution);
		this.warmStart(objectiveInitialSolution, initialSolution);
		this.pricingProblem = pricingProblem;
		this.setNodeOrdering(new Comparator<BAPNode>() {
			@Override
			public int compare(BAPNode node1, BAPNode node2) {
				if(node1.getBound()<=node2.getBound()) return -1;
				else return 1;
			}
		}); //Best Node First (BNF)
		//		this.setNodeOrdering(new BFSbapNodeComparator()); //Breadth-First Search (BFS)
		//		this.setNodeOrdering(new DFSbapNodeComparator()); //Depth-First Search (DFS)
	}

	/**
	 * Generates an artificial solution. Columns in the artificial solution are of high cost such that they never end up in the final solution
	 * if a feasible solution exists, since any feasible solution is assumed to be cheaper than the artificial solution. The artificial solution is used
	 * to guarantee that the master problem has a feasible solution.
	 * @return artificial solution
	 */
	@Override
	protected List<Route> generateInitialFeasibleSolution(BAPNode<EVRPTW,Route> node) {	
		//Dummy (artificial) routes to identify infeasibility
		HashMap<Integer, Integer> route=new HashMap<Integer, Integer>(dataModel.C);
		int[] routeSequence = new int[dataModel.C];
		for(int i=0; i< dataModel.C; i++) {route.put(i+1, 1); routeSequence[i] = i+1;}
		return Collections.singletonList(new Route("initSolution", true, route, routeSequence, pricingProblem, (int) Math.pow(10, 20), 0, 0, 0, 0.0, new ArrayList<Integer>(), 0, 0)); //dummy 
	}

	/**
	 * Checks whether the given node is integer
	 * @param node Node in the Branch-and-Price tree
	 * @return true if the solution is an integer solution
	 */
	@Override
	protected boolean isIntegerNode(BAPNode<EVRPTW, Route> node) {

		if(node.nodeID == 0) { //stores the information for the root node
			dataModel.columnsRootNode=master.getColumns(this.pricingProblem).size();
			dataModel.cutsRootNode=node.getInequalities().size();
		}

		boolean isInteger = true;
		List<Route> solution = node.getSolution();
		for(Route route: solution)
			if(route.value>0+PRECISION && route.value<1-PRECISION) {isInteger = false; break;}

		if(isInteger) return true;
		else {			//Inherit the routes generated
			List<Route> routesToAdd = new ArrayList<Route>();
			for(Route column: master.getColumns(this.pricingProblem)) {
				if(column.BBnode==-1) {
					column.BBnode=node.nodeID;
					routesToAdd.add(column);
				}
			}
			node.addInitialColumns(routesToAdd);
			//Inherit the cuts generated (not necessary)

			//Solve MIP at root node (optional)
			if(node.nodeID == 0) {
				try {solveIPAtRootNode(node);} 
				catch (IloException e) {e.printStackTrace();}
			}
			return false;
		}
	}

	/**
	 * To have a stronger upper bound, we solve the MIP at the root (with the generated columns)
	 */
	public void solveIPAtRootNode(BAPNode<EVRPTW, Route> node) throws IloException {

		Map<Route, IloIntVar> solution = new HashMap<Route, IloIntVar>();
		IloCplex cplex =new IloCplex(); 									//create CPLEX instance
		cplex.setOut(null);													//disable CPLEX output
		cplex.setParam(IloCplex.IntParam.Threads, config.MAXTHREADS); 		//set number of threads that may be used by the cplex

		//Define the objective
		IloObjective obj= cplex.addMinimize();
		//Define partitioning constraints
		IloRange[] visitCustomerConstraints=new IloRange[dataModel.C];
		for(int i=0; i< dataModel.C; i++)
			visitCustomerConstraints[i] = cplex.addEq(cplex.linearNumExpr(), 1, "visitCustomer_"+(i+1));

		//define constrains (capacitated station)
		IloRange[]  chargersCapacityConstraints = new IloRange[dataModel.last_charging_period];
		for (int t = 0; t < dataModel.last_charging_period; t++)
			chargersCapacityConstraints[t] = cplex.addLe(cplex.linearIntExpr(), dataModel.B, "capacity_"+(t+1));

		for(Route route: node.getInitialColumns()) {

			Route column = route.clone();
			//Register column with objective
			IloColumn iloColumn= cplex.column(obj,column.cost);

			//Register column with partitioning constraint
			for(int i: route.route.keySet())
				iloColumn=iloColumn.and(cplex.column(visitCustomerConstraints[i-1], column.route.get(i)));

			//Register column with chargers capacity constraints
			for (int t = column.initialChargingTime; t <= (column.initialChargingTime+ column.chargingTime-1); t++)
				iloColumn=iloColumn.and(cplex.column(chargersCapacityConstraints[t-1], 1));


			//Create the variable and store it
			IloIntVar var= cplex.intVar(iloColumn, 0, 1);
			cplex.add(var);
			solution.put(column, var);
		}

		//Set time limit
		cplex.setParam(IloCplex.DoubleParam.TiLim, 10.0); //set time limit in seconds (in this case 10 seconds)
		if(cplex.solve() && cplex.getStatus()==IloCplex.Status.Optimal && cplex.getCplexTime()<10){
			objectiveIncumbentSolution = (int) (cplex.getObjValue()+0.05);
			upperBoundOnObjective = objectiveIncumbentSolution;
			//retrieve solution
			List<Route> optimalSolution = new ArrayList<Route>();
			for (Route route: solution.keySet()) {
				double value = cplex.getValue(solution.get(route));
				if(value>=config.PRECISION){
					Route newRoute = route.clone();
					newRoute.value = value;
					optimalSolution.add(newRoute);
				}
			}
			incumbentSolution = optimalSolution;
		}
		cplex.close();
		cplex.end();
	}

	/**
	 * Solve a given Branch-and-Price node
	 * @param bapNode node in Branch-and-Price tree
	 * @param timeLimit future point in time by which the method must be finished
	 * @throws TimeLimitExceededException TimeLimitExceededException
	 */
	@Override
	protected void solveBAPNode(BAPNode<EVRPTW,Route> bapNode, long timeLimit) throws TimeLimitExceededException {
		customCG cg=null;
		try {
			cg = new customCG(dataModel, master, pricingProblems, solvers, pricingProblemManager, bapNode.getInitialColumns(), objectiveIncumbentSolution, bapNode.getBound()); //Solve the node
			for(CGListener listener : columnGenerationEventListeners) cg.addCGEventListener(listener);
			cg.solve(timeLimit);
		}finally{
			//Update statistics
			if(cg != null) {
				timeSolvingMaster += cg.getMasterSolveTime();
				timeSolvingPricing += cg.getPricingSolveTime();
				totalNrIterations += cg.getNumberOfIterations();
				totalGeneratedColumns += cg.getNrGeneratedColumns();
				//				if(cg.incumbentSolutionObjective<=this.objectiveIncumbentSolution) {this.objectiveIncumbentSolution = cg.incumbentSolutionObjective; this.incumbentSolution=cg.incumbentSolution;}
				notifier.fireFinishCGEvent(bapNode, cg.getBound(), cg.getObjective(), cg.getNumberOfIterations(), cg.getMasterSolveTime(), cg.getPricingSolveTime(), cg.getNrGeneratedColumns());
			}
		}
		ArrayList<Route> solution = new ArrayList<Route>(cg.getSolution().size()); //if not, it overwrites the value
		for(Route route: cg.getSolution()) {Route newRoute = route.clone(); newRoute.value = route.value; solution.add(newRoute);}
		bapNode.storeSolution(cg.getObjective(), cg.getBound(), solution, cg.getCuts());
	}

	/**
	 * Test whether the given node can be pruned based on this bounds
	 * @param node node
	 * @return true if the node can be pruned
	 */
	@Override
	protected boolean nodeCanBePruned(BAPNode<EVRPTW,Route> node){
		//		System.out.println(Math.ceil(node.getBound()-config.PRECISION) + " >= " + this.objectiveIncumbentSolution);
		return Math.ceil(node.getBound()) >= (this.objectiveIncumbentSolution-config.PRECISION);
	}
}