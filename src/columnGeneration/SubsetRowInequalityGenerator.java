package columnGeneration;

import ilog.concert.IloException;

import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import model.EVRPTW;
import java.util.*;
import org.jorlib.frameworks.columnGeneration.master.cutGeneration.AbstractCutGenerator;
import org.jorlib.frameworks.columnGeneration.master.cutGeneration.AbstractInequality;
import columnGeneration.SubsetRowSeparator.PreliminaryCut;


/**
 * This class checks for violated subset-row cuts in the master problem. 
 * It also adds (some of) them to the master problem.
 */
public final class SubsetRowInequalityGenerator extends AbstractCutGenerator<EVRPTW, VRPMasterData> {

	private final SubsetRowSeparator separator;		//class to separate the cuts

	/** Creates a new SRCs generator. */
	public SubsetRowInequalityGenerator(EVRPTW modelData) {
		super(modelData, "SRC Generator");
		separator=new SubsetRowSeparator(dataModel); //creates a SRC separator
	}

	/**
	 * Generate inequalities using the data originating from the master problem.
	 * @return Returns true if a violated inequality has been found.
	 * When violated inequalities are found, they are added to the MP through the addCut method.
	 */
	@Override
	public List<AbstractInequality> generateInqualities() {

		//Check for violated SRC. When found, generate an inequality
		long startTime = System.currentTimeMillis();
		separator.separateRow(masterData.routeValueMap);
		if(separator.SRCViolation()){
			List<AbstractInequality> cuts = new ArrayList<>(separator.getCutSets().size());
			for(PreliminaryCut preliminaryCut: separator.getCutSets()) {
				SubsetRowInequality inequality=new SubsetRowInequality(this, preliminaryCut.cutSet, preliminaryCut.violation);
				try {
					this.addCut(inequality);
				} catch (IloException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				cuts.add(inequality);
			}
			long endTime = System.currentTimeMillis();
			logger.debug("Time separating cuts (s): " + getTimeInSeconds(endTime-startTime));
			return cuts;
		}
		return Collections.emptyList();
	}

	/**
	 * If a violated inequality has been found add it to the master problem.
	 * @param subsetRowInequality
	 * Handle the corresponding cplex object in the MasterData so that the Cut Generator Directly add a constraint. 
	 * @throws IloException 
	 */
	private void addCut(SubsetRowInequality subsetRowInequality) throws IloException{

		if(masterData.subsetRowInequalities.containsKey(subsetRowInequality))
			throw new RuntimeException("Error, duplicate subset-row cut is being generated! This cut should already exist in the master problem: "+subsetRowInequality);
		try {
			logger.debug("Adding SRC: " +  subsetRowInequality);
			IloLinearNumExpr expr=masterData.cplex.linearNumExpr();
			//Register the columns with this constraint.
			for(Route route: masterData.getColumnsForPricingProblemAsList(masterData.pricingProblem)){
				if(route.isArtificialColumn) continue;
				//Check the number of visits to the customers in the triplet
				int coeff = getCoefficient(route, subsetRowInequality);
				if(coeff>0){
					IloNumVar var=masterData.getVar(masterData.pricingProblem,route);
					expr.addTerm(coeff, var);
				}
			}
			IloRange subsetRowConstraint = masterData.cplex.addLe(expr, 1, "subsetRow_"+Arrays.toString(subsetRowInequality.cutSet));
			masterData.subsetRowInequalities.put(subsetRowInequality, subsetRowConstraint);
		} catch (IloException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Computes the coefficient of a route in a SRC
	 * @param route for which the coefficient is calculated
	 * @param subsetRowInequality considered
	 */
	public int getCoefficient(Route route, SubsetRowInequality subsetRowInequality) {
		int visits = 0;
		for(int i: subsetRowInequality.cutSet)
			visits+=route.route.getOrDefault(i, 0);
		return (int) Math.floor(0.5*visits);
	}


	/**
	 * Add a SRC to the Master Problem
	 * @param cut AbstractInequality
	 */
	@Override
	public void addCut(AbstractInequality cut) {
		if(!(cut instanceof SubsetRowInequality))
			throw new IllegalArgumentException("This AbstractCutGenerator can ONLY add Subset-row cuts");
		SubsetRowInequality subSetRowInequality=(SubsetRowInequality) cut;
		try {
			this.addCut(subSetRowInequality);
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * List of inequalities that have been generated
	 * @return generated inequalities
	 */
	@Override
	public List<AbstractInequality> getCuts() {
		return new ArrayList<>(masterData.subsetRowInequalities.keySet());
	}

	/**
	 * Close the generator
	 */
	@Override
	public void close() {} //Nothing to do here

	/** Returns the time in seconds (and considering two decimals). */
	public double getTimeInSeconds(double time) {
		double realTime = time*0.001;
		realTime = Math.floor(realTime*100)/100; //two decimals
		return realTime;
	}
}