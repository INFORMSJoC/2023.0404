package columnGeneration;
import java.util.Arrays;
import org.jorlib.frameworks.columnGeneration.master.cutGeneration.AbstractCutGenerator;
import org.jorlib.frameworks.columnGeneration.master.cutGeneration.AbstractInequality;

/**
 * This class represents a SubsetRowInequality (See Jepsen et al. 2008)
 * It considers customer sets of size three
 */
public final class SubsetRowInequality extends AbstractInequality {

	public final int[] cutSet; 				//customer triplets
	public double violation; 				//current violation of the cut

	public SubsetRowInequality(AbstractCutGenerator maintainingGenerator, int[] cutSet, double violation) {
		super(maintainingGenerator);
		this.cutSet=cutSet;
		this.violation = violation;
	}

	/** The equals and hashCode methods are important (for the jORlib)**/
	@Override
	public boolean equals(Object o) { //Important for jORlib
		if(this==o)
			return true;
		else if(!(o instanceof SubsetRowInequality))
			return false;
		SubsetRowInequality other=(SubsetRowInequality)o;
		return Arrays.equals(this.cutSet, other.cutSet);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(cutSet);
	}

	/** String representation of the SRC */
	@Override
	public String toString(){
		return ""+Arrays.toString(this.cutSet)+", violation: " + Math.floor(10000*this.violation)/10000;
	}

	/** Copy of the SRC */
	@Override
	public SubsetRowInequality clone() {
		return new SubsetRowInequality(maintainingGenerator, this.cutSet.clone(), this.violation);
	}

}