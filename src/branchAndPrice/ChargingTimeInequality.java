package branchAndPrice;

/**
 * This class represent a branching decision inequality
 */
public class ChargingTimeInequality {

	public int coefficient;						//coefficient associated with the new constraint
	public boolean lessThanOrEqual;				//whether it is a <= constraint (it can be >= as well) 
	public int timestep;						//charging time associated with the branching decision
	public boolean startCharging;				//whether is an start charging arc (0,t) or the an charging arc (t,s)

	/** 
	 * Constructor of the class
	 * @param coefficient of the branching constraint (must be integer)
	 * @param lessThanOrEqual true if it is a less-than-or-equal to constraint
	 * **/
	public ChargingTimeInequality(int coefficient, boolean lessThanOrEqual, int timestep, boolean startCharging) {
		this.coefficient = coefficient;
		this.lessThanOrEqual = lessThanOrEqual;
		this.timestep = timestep;
		this.startCharging = startCharging;
	}

	@Override
	public int hashCode() {
		return coefficient+Boolean.hashCode(lessThanOrEqual)+timestep+Boolean.hashCode(startCharging);
	}

	/**
	 * Obtains the string of the branching decision
	 */
	@Override
	public String toString(){
		if(startCharging) {
			if(lessThanOrEqual) return "y(s,"+this.timestep+")<="+coefficient;
			else return "y(s,"+this.timestep+")>="+coefficient;
		}else {
			if(lessThanOrEqual) return "y("+this.timestep+",0)<="+coefficient;
			else return "y("+this.timestep+",0)>="+coefficient;
		}
	}
}