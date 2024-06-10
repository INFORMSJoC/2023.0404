package columnGeneration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.jorlib.frameworks.columnGeneration.colgenMain.AbstractColumn;
import model.EVRPTW;


/**
 * Implementation of a column (route) in the mE-VRSPTW
 */
public final class Route extends AbstractColumn<EVRPTW, PricingProblem> {

	public final HashMap<Integer, Integer> route; 	//number of times each customer is visited
	public final int[] routeSequence;				//route sequence
	public final int cost;							//cost of this column in the objective 
	public final int departureTime;					//departure time
	public final int energy;						//energy required by the route
	public final int load;							//load
	public double reducedCost;						//reduced cost (when priced)
	public ArrayList<Integer> arcs;					//used arcs

	//charging information
	public int initialChargingTime;
	public int chargingTime;

	public int BBnode=-1;						//Node in the BB tree in which it was priced

	/**
	 * Creates a new route (column). 
	 * @param creator: description of who created the column (an algorithm)
	 * @param isArtificial: indicated whether the route is artificial (for initialization purposes)
	 */
	public Route(String creator, boolean isArtificial, HashMap<Integer, Integer> route, int[] routeSequence, PricingProblem pricingProblem, int cost, int departureTime, int energy, int load, double reducedCost, ArrayList<Integer> arcs, int initialChargingTime, int chargingTime) {
		super(pricingProblem, isArtificial, creator);
		this.route=route;
		this.routeSequence = routeSequence;
		this.cost= cost;
		this.departureTime = departureTime;
		this.energy = energy;
		this.load = load;
		this.reducedCost = reducedCost;
		this.arcs = arcs;
		this.initialChargingTime = initialChargingTime;
		this.chargingTime = chargingTime;
	}

	/** The equals and hashCode methods are important (for the jORlib). **/
	@Override
	public boolean equals(Object o) {
		if(this==o)
			return true;
		if(!(o instanceof Route))
			return false;
		Route other=(Route) o;
		if((this.isArtificialColumn && !other.isArtificialColumn)|| (!this.isArtificialColumn && other.isArtificialColumn)) return false;
		return (this.arcs.equals(other.arcs) && this.initialChargingTime == other.initialChargingTime && this.chargingTime == other.chargingTime);
	}

	@Override
	public int hashCode() {
		return route.hashCode()+initialChargingTime+chargingTime;
	}

	/** Returns the string representation of a route. */
	@Override
	public String toString() {
		if (this.value>0)
			return "Value: "+ Math.floor(10000*this.value)/10000+" Cost: "+this.cost +" Energy: " + this.energy + " Route: "+ Arrays.toString(this.routeSequence)+" charging time: [" + this.initialChargingTime + "," + (this.initialChargingTime+ this.chargingTime-1)+"]"+ " Departure: "  + this.departureTime +  " Arcs:" + arcs.toString();
		else 
			return "Reduced Cost: "+ Math.floor(10000*this.reducedCost)/10000+" Cost: "+this.cost +" Energy: " + this.energy +" Route: "+ Arrays.toString(this.routeSequence)+ " charging time: [" + this.initialChargingTime + "," + (this.initialChargingTime+ this.chargingTime-1)+"]"+ " Departure: "  + this.departureTime + " Arcs:" + arcs.toString();
	}

	/** Clones the route. */
	public Route clone() {
		return new Route(this.creator, this.isArtificialColumn, (HashMap<Integer, Integer>) this.route.clone(), (int[]) this.routeSequence.clone(), this.associatedPricingProblem, this.cost, this.departureTime, this.energy, this.load, this.reducedCost, (ArrayList<Integer>) this.arcs.clone(), this.initialChargingTime, this.chargingTime);
	}
}