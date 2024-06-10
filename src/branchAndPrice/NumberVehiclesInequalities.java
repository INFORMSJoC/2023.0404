package branchAndPrice;

/**
 * This class represent a branching decision on the number of vehicles used
 */
public class NumberVehiclesInequalities {

	public int coefficient;				//RHS of the new constraint
	public boolean lessThanOrEqual;		//whether it is a <= constraint (it can be >= as well)

	/** 
	 * Constructor of the class
	 * @param coefficient of the branching constraint (must be integer)
	 * @param lessThanOrEqual true if it is a less-than-or-equal to constraint
	 * **/
	public NumberVehiclesInequalities(int coefficient, boolean lessThanOrEqual) {
		this.coefficient = coefficient;
		this.lessThanOrEqual = lessThanOrEqual;
	}

	@Override
	public int hashCode() {
		return coefficient+Boolean.hashCode(lessThanOrEqual);
	}

	/**
	 * Obtains the string of the branching decision
	 */
	@Override
	public String toString(){
		if(lessThanOrEqual)return "<="+coefficient;
		else return ">="+coefficient;
	}
}