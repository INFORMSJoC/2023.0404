package columnGeneration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import model.EVRPTW;

/**
 * This class separates subset-row inequalities by complete enumeration.
 */
public class SubsetRowSeparator{

	public static final double PRECISION=0.01; 			//precision considered for the cuts
	public static double minimumThreshold = 0.1; 		//minimum violation for the cut with largest violation
	public static int maximumNumberCuts = 30; 			//maximum number of cuts to add each iteration
	public static int maxCutsPerCustomer = 5; 			//maximum cuts where a customer appears (diversification)
	private boolean SRCViolation=false; 				//if at least one violation has been found
	private ArrayList<PreliminaryCut> cutSets; 			//customers triplets
	private EVRPTW dataModel; 							//original data which defines the mE-VRSPTW

	/**
	 * This method instantiates the SRCs Separator.
	 */
	public SubsetRowSeparator(EVRPTW dataModel){
		this.dataModel=dataModel;
	}

	/**
	 * Starts the SR separation.
	 */
	public void separateRow(Map<Route, Double> routeValueMap){

		//candidate for cuts
		ArrayList<PreliminaryCut> preliminaryCutSet = new ArrayList<PreliminaryCut>(2*maximumNumberCuts);

		//You can remove from the enumeration all customers that are visited in routes with a flow of 1
		boolean[] customersToDiscard = new boolean[dataModel.C]; //true if the customer should be discarded

		for(Map.Entry<Route, Double> entry : routeValueMap.entrySet()) {
			if(entry.getValue()>=0.999){
				for (int arc: entry.getKey().arcs) {if(dataModel.arcs[arc].head!=dataModel.C+1) customersToDiscard[dataModel.arcs[arc].head-1]= true;}
			}
		}

		//Find by enumeration the SRC violations
		for (int i = 0; i < dataModel.C; i++) {
			if(customersToDiscard[i]) continue;
			for (int j = i+1; j < dataModel.C; j++) {
				if(customersToDiscard[j]) continue;
				for (int k = j+1; k < dataModel.C; k++) {
					if(customersToDiscard[k]) continue;
					double coeff = 0;
					for(Map.Entry<Route, Double> entry : routeValueMap.entrySet()){
						coeff+= Math.floor(0.5*(entry.getKey().route.getOrDefault(i+1, 0)+entry.getKey().route.getOrDefault(j+1, 0)+entry.getKey().route.getOrDefault(k+1, 0)))*entry.getValue();
					}
					if (coeff>1+PRECISION) {
						int[] customerTriplet = new int[3];
						customerTriplet[0]=i+1;customerTriplet[1]=j+1; customerTriplet[2]=k+1;
						preliminaryCutSet.add(new PreliminaryCut(customerTriplet, coeff-1));
					}
				}
			}
		}
		//Diversify the cuts
		this.cutSets = new ArrayList<PreliminaryCut>(maximumNumberCuts);
		Collections.sort(preliminaryCutSet, new SortByViolation());
		if(preliminaryCutSet.size()==0 || preliminaryCutSet.get(0).violation<minimumThreshold) {SRCViolation=false; return;}
		else SRCViolation = true;
		int[] cutsWithCustomer = new int[dataModel.C];
		for(PreliminaryCut cut: preliminaryCutSet) {
			boolean add = true;
			for(int i: cut.cutSet) if(cutsWithCustomer[i-1]>=maxCutsPerCustomer) {add = false; break;}
			if (add) {
				for(int i: cut.cutSet) cutsWithCustomer[i-1]+=1;
				cutSets.add(cut);
				if(cutSets.size()>=maximumNumberCuts) return;
			}
		}
	}

	/** Returns whether a SRC violation exists in the fractional solution. */
	public boolean SRCViolation(){
		return SRCViolation;
	}

	/** Returns the sets of customers triplets for which an SRC has been separated */
	public ArrayList<PreliminaryCut> getCutSets(){
		return cutSets;
	}

	/** Class that represent a preliminary cut that could be added */
	public class PreliminaryCut{
		public int[] cutSet; 					//customer triplet
		public double violation; 				//current violation of the cut
		List<Route> contributingRoutes; 		//routes contributing to the cut

		public PreliminaryCut(int[] cutSet, double violation) {
			this.cutSet = cutSet;
			this.violation = violation;
		}
	}

	/** @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.*/
	public class SortByViolation implements Comparator<PreliminaryCut> {
		@Override
		public int compare(PreliminaryCut cut1, PreliminaryCut cut2) {
			if(cut1.violation>cut2.violation) return -1;
			if(cut1.violation<cut2.violation) return 1;
			return 0;
		}
	}
}