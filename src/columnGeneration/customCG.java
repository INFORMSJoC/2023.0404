package columnGeneration;

import java.util.ArrayList;
import java.util.List;
import org.jorlib.frameworks.columnGeneration.colgenMain.ColGen;
import org.jorlib.frameworks.columnGeneration.io.TimeLimitExceededException;
import org.jorlib.frameworks.columnGeneration.master.AbstractMaster;
import org.jorlib.frameworks.columnGeneration.master.MasterData;
import org.jorlib.frameworks.columnGeneration.master.OptimizationSense;
import org.jorlib.frameworks.columnGeneration.pricing.AbstractPricingProblemSolver;
import org.jorlib.frameworks.columnGeneration.pricing.PricingProblemManager;
import model.EVRPTW;

/**
 * This class is a custom implementation of the ColGen class (jORLib)
 * It is implemented to compute a lower bound on the master problem
 */
public class customCG extends ColGen<EVRPTW, Route, PricingProblem> {

	public ArrayList<Route> incumbentSolution = new ArrayList<Route>(); 	//stores the incumbent solution found throughout the CG
	public int incumbentSolutionObjective = (int) Double.MAX_VALUE; 		// stores the incumbent solution objective found throughout the CG

	public customCG(EVRPTW dataModel, AbstractMaster<EVRPTW, Route, PricingProblem, ? extends MasterData> master,
			PricingProblem pricingProblem,
			List<Class<? extends AbstractPricingProblemSolver<EVRPTW, Route, PricingProblem>>> solvers,
			List<Route> initSolution, int cutoffValue, double boundOnMasterObjective) {
		super(dataModel, master, pricingProblem, solvers, initSolution, cutoffValue, boundOnMasterObjective);
		// TODO Auto-generated constructor stub
	}

	public customCG(EVRPTW dataModel, AbstractMaster<EVRPTW, Route, PricingProblem, ? extends MasterData> master,
			List<PricingProblem> pricingProblems,
			List<Class<? extends AbstractPricingProblemSolver<EVRPTW, Route, PricingProblem>>> solvers,
			PricingProblemManager<EVRPTW, Route, PricingProblem> pricingProblemManager, List<Route> initSolution,
			int cutoffValue, double boundOnMasterObjective) {
		super(dataModel, master, pricingProblems, solvers, pricingProblemManager, initSolution, cutoffValue,
				boundOnMasterObjective);
		// TODO Auto-generated constructor stub
	}

	public customCG(EVRPTW arg0, AbstractMaster<EVRPTW, Route, PricingProblem, ? extends MasterData> arg1,
			List<PricingProblem> arg2,
			List<Class<? extends AbstractPricingProblemSolver<EVRPTW, Route, PricingProblem>>> arg3, List<Route> arg4,
			int arg5, double arg6) {
		super(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected double calculateBoundOnMasterObjective(
			Class<? extends AbstractPricingProblemSolver<EVRPTW, Route, PricingProblem>> solver) {
		// TODO Auto-generated method stub
		return master.getBoundComponent(); //obtains a lower bound on the master problem objective
	}


	/**
	 * Solve the Column Generation problem. First the master problem is solved. Next the pricing problems(s) is (are) solved. To solve the pricing problems, the pricing
	 * solvers are invoked one by one in a hierarchical fashion. First the first solver is invoked to solve the pricing problems. Any new columns generated are immediately returned.
	 * If it fails to find columns, the next solver is invoked and so on. If the pricing problem discovers new columns, they are added to the master problem and the method continues
	 * with the next column generation iteration.<br>
	 * If no new columns are found, the method checks for violated inequalities. If there are violated inequalities, they are added to the master problem and the method continues with the
	 * next column generation iteration.<br>
	 * The solve procedure terminates under any of the following conditions:
	 * <ol>
	 * <li>the solver could not identify new columns</li>
	 * <li>Time limit exceeded</li>
	 * <li>The bound on the best attainable solution to the master problem is worse than the cutoff value. Assuming that the master is a minimization problem, the Colgen procedure is terminated if {@code ceil(boundOnMasterObjective) >= cutoffValue}</li>
	 * <li>The solution to the master problem is provable optimal, i.e the bound on the best attainable solution to the master problem equals the solution of the master problem.</li>
	 * </ol>
	 * @param timeLimit Future point in time (ms) by which the procedure should be finished. Should be defined as: {@code System.currentTimeMilis()+<desired runtime>}
	 * @throws TimeLimitExceededException Exception is thrown when time limit is exceeded
	 */
	@Override
	public void solve(long timeLimit) throws TimeLimitExceededException{
		//Set time limit pricing problems
		pricingProblemManager.setTimeLimit(timeLimit);
		colGenSolveTime=System.currentTimeMillis();
		this.incumbentSolutionObjective = this.cutoffValue;

		boolean foundNewColumns=false; 				//identify whether the pricing problem generated new columns
		boolean hasNewCuts; 						//identify whether the master problem violates any valid inequalities
		notifier.fireStartCGEvent();
		do{
			nrOfColGenIterations++;
			hasNewCuts=false;

			//Solve the master
			this.invokeMaster(timeLimit);
			if(objectiveMasterProblem<boundOnMasterObjective-dataModel.precision) {
				System.out.println("Check problems with LB!");
				System.exit(-1);
			}

			//We can stop when the optimality gap is closed. We still need to check for violated inequalities though.
			if(Math.abs(objectiveMasterProblem - boundOnMasterObjective)<config.PRECISION){
				//Check whether there are inequalities. Otherwise potentially an infeasible integer solution (e.g. TSP solution with subtours) might be returned.
				if(config.CUTSENABLED){
					long time=System.currentTimeMillis();
					hasNewCuts=master.hasNewCuts();
					masterSolveTime+=(System.currentTimeMillis()-time); //Generating inequalities is considered part of the master problem
					if(hasNewCuts)
						continue;
					else
						break;
				}else
					break;
			}

			//Solve the pricing problem and possibly update the bound on the master problem objective
			List<Route> newColumns=this.invokePricingProblems(timeLimit); //List containing new columns generated by the pricing problem
			foundNewColumns=!newColumns.isEmpty();

			//Check whether the boundOnMasterObjective exceeds the cutoff value
			if(boundOnMasterExceedsCutoffValue())
				break;
			else if(System.currentTimeMillis() >= timeLimit){ 		//check whether we are still within the timeLimit
				notifier.fireTimeLimitExceededEvent();
				throw new TimeLimitExceededException();
			}else if(config.CUTSENABLED && !foundNewColumns){ 		//check for inequalities. This can only be done if the master problem hasn't changed (no columns can be added).
				long time=System.currentTimeMillis();
				hasNewCuts=master.hasNewCuts();
				masterSolveTime+=(System.currentTimeMillis()-time); //generating inequalities is considered part of the master problem
			}

		}while(foundNewColumns || hasNewCuts);
		colGenSolveTime=System.currentTimeMillis()-colGenSolveTime;
		notifier.fireFinishCGEvent();
	}

	/**
	 * Invokes the solve method of the Master Problem, fires corresponding events and queries the results.
	 * @param timeLimit Future point in time by which the Master Problem must be finished
	 * @throws TimeLimitExceededException TimeLimitExceededException
	 */
	protected void invokeMaster(long timeLimit) throws TimeLimitExceededException {
		notifier.fireStartMasterEvent();
		long time=System.currentTimeMillis();
		master.solve(timeLimit);
		objectiveMasterProblem =master.getObjective();
		masterSolveTime+=(System.currentTimeMillis()-time);

		//Check if we have found an integer solution
		boolean isInteger = true;
		for(Route route: master.getSolution())
			if(route.value>0+config.PRECISION && route.value<1-config.PRECISION) {isInteger = false; break;}

		//Update incumbent solution
		if(isInteger && this.cutoffValue>master.getObjective()) {
			this.incumbentSolution = new ArrayList<>();
			this.cutoffValue = (int) (master.getObjective()+0.5);
			this.incumbentSolutionObjective = this.cutoffValue;
			for(Route route: master.getSolution()) {
				Route newRoute = route.clone();
				newRoute.value = route.value;
				this.incumbentSolution.add(newRoute);
			}
		}
		notifier.fireFinishMasterEvent();
	}

	/**
	 * Invokes the solve methods of the algorithms which solve the Pricing Problem. In addition, after solving the Pricing Problems
	 * and before any new columns are added to the Master Problem, this method invokes the {@link #calculateBoundOnMasterObjective(Class solver) calculateBoundOnMasterObjective} method.
	 * @param timeLimit Future point in time by which the Pricing Problem must be finished
	 * @return list of new columns which have to be added to the Master Problem, or an empty list if no columns could be identified
	 * @throws TimeLimitExceededException TimeLimitExceededException
	 */
	@Override
	protected List<Route> invokePricingProblems(long timeLimit) throws TimeLimitExceededException {
		//Solve the pricing problem
		List<Route> newColumns=new ArrayList<Route>();
		long time=System.currentTimeMillis();

		//Update data in pricing problems
		for(PricingProblem pricingProblem : pricingProblems){
			master.initializePricingProblem(pricingProblem);
		}

		//Solve pricing problems in the order of the pricing algorithms
		notifier.fireStartPricingEvent();
		pricingProblemManager.setTimeLimit(timeLimit);
		boolean exact = false;
		for(Class<? extends AbstractPricingProblemSolver<EVRPTW, Route, PricingProblem>> solver : solvers){
			newColumns=pricingProblemManager.solvePricingProblems(solver);

			//Stop when we found new columns
			if(!newColumns.isEmpty()){
				break;
			}
			exact = true;
		}

		if(exact) 
			if(!newColumns.isEmpty()) this.boundOnMasterObjective =(optimizationSenseMaster == OptimizationSense.MINIMIZE ? Math.max(boundOnMasterObjective,this.calculateBoundOnMasterObjective(solvers.get(1))) : Math.min(boundOnMasterObjective,this.calculateBoundOnMasterObjective(solvers.get(1))));
			else this.boundOnMasterObjective = master.getObjective(); //update the bound before adding cuts

		notifier.fireFinishPricingEvent(newColumns);

		pricingSolveTime+=(System.currentTimeMillis()-time);
		nrGeneratedColumns+=newColumns.size();
		//Add columns to the master problem
		if(!newColumns.isEmpty()){
			for(Route column : newColumns){
				master.addColumn(column);
			}
		}
		return newColumns;
	}
}