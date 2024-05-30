package columnGeneration;

import model.EVRPTW;
import model.EVRPTW.Arc;
import model.EVRPTW.Vertex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.branchingDecisions.BranchingDecision;
import org.jorlib.frameworks.columnGeneration.pricing.AbstractPricingProblemSolver;
import branchAndPrice.FixArc;
import branchAndPrice.RemoveArc;


/**
 * This class provides an exact solver for the ng-SPPRC pricing problem
 */
public final class ExactLabelingMultigraphPricingProblemSolver extends AbstractPricingProblemSolver<EVRPTW, Route, PricingProblem> {

	public Vertex[] vertices = dataModel.vertices; 		//vertices of the instance
	public PriorityQueue<Vertex> nodesToProcess; 		//labels that need be processed
	public final int numCols = 400; 					//maximum number of routes (columns) allowed
	public boolean[] infeasibleArcs; 					//arcs that cannot be used by branching
	public final int similarityThreshold = 5; 			//diversification of columns


	/**
	 * Labeling algorithm to solve the ng-SPPRC
	 */
	public ExactLabelingMultigraphPricingProblemSolver(EVRPTW dataModel, PricingProblem pricingProblem) {
		super(dataModel, pricingProblem);
		this.name="ExactLabelingSolver";
		this.infeasibleArcs = new boolean[dataModel.numArcs];
		this.nodesToProcess = new PriorityQueue<Vertex>(dataModel.V, new SortVertices());
	}

	/**
	 * Runs the labeling algorithm
	 */
	public void runLabeling() {

		//Initialization
		Label initialLabel = new Label(dataModel.C+1, dataModel.C+1, 0, -pricingProblem.dualCost, dataModel.Q, vertices[dataModel.C+1].closing_tw, dataModel.E, 0,new boolean[dataModel.C], new boolean[dataModel.C], new boolean[pricingProblem.subsetRowCuts.size()], new HashSet<Integer>(pricingProblem.subsetRowCuts.size()));
		this.nodesToProcess.add(vertices[dataModel.C+1]);
		initialLabel.index = 0;
		vertices[dataModel.C+1].unprocessedLabels.add(initialLabel);

		//Labeling algorithm
		long startTime = System.currentTimeMillis();
		while (!nodesToProcess.isEmpty() && vertices[dataModel.V].unprocessedLabels.size()<= numCols && System.currentTimeMillis()<timeLimit) {
			ArrayList<Label> labelsToProcessNext = labelsToProcessNext();
			for(Label currentLabel: labelsToProcessNext) {
				boolean isDominated = checkDominance(currentLabel);
				if(isDominated) continue;
				else {currentLabel.index = vertices[currentLabel.vertex].processedLabels.size(); vertices[currentLabel.vertex].processedLabels.add(currentLabel);}
				for(Arc a: dataModel.graph.incomingEdgesOf(currentLabel.vertex)) {
					if(infeasibleArcs[a.id]) continue;
					Label extendedLabel;
					if(a.tail<=dataModel.C) extendedLabel = extendLabel(currentLabel, a);
					else extendedLabel = extendLabelChargingTime(currentLabel, a);
					if (extendedLabel!=null) {
						updateNodesToProcess(extendedLabel);
					}
				}
			}
		}

		long totalTime = System.currentTimeMillis()-startTime;
		dataModel.exactPricingTime+=totalTime;
		logger.debug("Time solving (exactly) the pricing problem (s): " + getTimeInSeconds(totalTime)); 
	}

	/**
	 * Selects a set of labels to process (the one with the most remaining load)
	 */
	public ArrayList<Label> labelsToProcessNext(){

		ArrayList<Label> labelsToProcessNext = new ArrayList<Label>();
		Vertex currentVertex = nodesToProcess.poll();
		while(true) {
			Label currentLabel = currentVertex.unprocessedLabels.poll();
			if(labelsToProcessNext.isEmpty()) labelsToProcessNext.add(currentLabel);
			else {
				boolean isDominated = false;
				for(Label L2: labelsToProcessNext) {
					isDominated = isDominated(currentLabel, L2);
					if(isDominated) break;
				}
				if(!isDominated) labelsToProcessNext.add(currentLabel);
			}
			if(currentVertex.unprocessedLabels.isEmpty() || (currentVertex.id<=dataModel.C && currentVertex.unprocessedLabels.peek().remainingLoad<currentLabel.remainingLoad)) break;
		}
		if(!currentVertex.unprocessedLabels.isEmpty()) nodesToProcess.add(currentVertex);
		return labelsToProcessNext;
	}

	/**
	 * Given a new (non-dominated) label, updates the nodes to be processed
	 */
	public void updateNodesToProcess(Label extendedLabel) {
		Vertex currentVertex = vertices[extendedLabel.vertex];
		if(currentVertex.id == dataModel.V) vertices[extendedLabel.vertex].unprocessedLabels.add(extendedLabel);
		else if(currentVertex.unprocessedLabels.isEmpty()) {currentVertex.unprocessedLabels.add(extendedLabel); nodesToProcess.add(currentVertex);}
		else currentVertex.unprocessedLabels.add(extendedLabel);
	}

	/**
	 * Label extension procedure
	 */
	public Label extendLabel(Label currentLabel, Arc arc) {

		int source = arc.tail;
		if (source>=1 && source<=dataModel.C) {
			if (currentLabel.unreachable[source-1]|| currentLabel.ng_path[source-1]) return null;
		}

		double reducedCost = currentLabel.reducedCost+arc.modifiedCost;

		boolean[] eta = currentLabel.eta.clone();
		HashSet<Integer> srcIndices = new HashSet<Integer>(currentLabel.srcIndices);
		for(int srcIndex: vertices[source].SRCIndices) {
			if(currentLabel.eta[srcIndex]) {
				eta[srcIndex] = false;
				int dualIndex = dataModel.C+dataModel.last_charging_period+srcIndex;
				reducedCost-=pricingProblem.dualCosts[dualIndex];
				srcIndices.remove(srcIndex);
			}
			else {eta[srcIndex]=true; srcIndices.add(srcIndex);}
		}
		reducedCost = Math.floor(reducedCost*10000)/10000;

		//Only negative reduced cost labels
		if (source==0 && reducedCost>= pricingProblem.reducedCostThreshold-dataModel.precision) return null;

		int remainingLoad = currentLabel.remainingLoad-vertices[source].load;
		int remainingTime = currentLabel.remainingTime-arc.time;
		if(remainingTime>vertices[source].closing_tw) remainingTime = vertices[source].closing_tw;

		int remainingEnergy = currentLabel.remainingEnergy-arc.energy;
		if(remainingEnergy<0) return null;
		int chargingTime = dataModel.f_inverse[dataModel.E-remainingEnergy];

		//Quick check
		if(source>0 && remainingTime-dataModel.graph.getEdge(0, source).minimumTime<vertices[0].opening_tw) return null;
		if(source>0 && remainingEnergy-dataModel.graph.getEdge(0, source).minimumEnergy<0) return null;

		//Check whether the extension is actually feasible
		if(remainingTime<vertices[source].opening_tw || chargingTime>= (int) (remainingTime/10)) return null;

		boolean[] unreachable = Arrays.copyOf(currentLabel.unreachable.clone(), currentLabel.unreachable.length);
		boolean[] ng_path = new boolean[dataModel.C];
		if(source>0) ng_path[source-1] = true;
		else ng_path = Arrays.copyOf(currentLabel.ng_path, currentLabel.ng_path.length);

		//Mark unreachable customers and ng-path cycling restrictions
		if(source>0) {
			for(int i: vertices[source].unreachable) unreachable[i-1] = true;
			int lastTail = -1;
			for (Arc c: dataModel.graph.incomingEdgesOf(source)) {
				if(c.tail==lastTail || c.tail==0 || unreachable[c.tail-1]) continue;
				//unreachable
				if (remainingLoad-vertices[c.tail].load<0 || remainingTime-c.minimumTime<vertices[c.tail].opening_tw || 
						remainingEnergy-c.minimumEnergy<0 || 
						Math.min(remainingTime-c.minimumTime, vertices[c.tail].closing_tw)-dataModel.graph.getEdge(0, c.tail).minimumTime<vertices[0].opening_tw
						|| remainingEnergy-c.minimumEnergy - dataModel.graph.getEdge(0, c.tail).minimumEnergy<0) {
					unreachable[c.tail-1] = true;
				}
				//ng-path
				if (currentLabel.ng_path[c.tail-1] && vertices[source].neighbors.contains(c.tail)) ng_path[c.tail-1] = true;
				else ng_path[c.tail-1] = false;
				lastTail = c.tail;
			}
		}
		Label extendedLabel = new Label(source, arc.id, currentLabel.index, reducedCost, remainingLoad, remainingTime, remainingEnergy, chargingTime,unreachable, ng_path, eta, srcIndices);
		return extendedLabel;
	}

	/**
	 * Label extension procedure
	 */
	public Label extendLabelChargingTime(Label currentLabel, Arc arc) {

		int source = arc.tail;
		if(arc.head==0 && (source-dataModel.V<currentLabel.chargingTime || source-dataModel.V>=currentLabel.remainingTime/10)) return null;
		if(source == dataModel.V && (currentLabel.chargingTime>0 || currentLabel.reducedCost>-dataModel.precision)) return null;

		double reducedCost = currentLabel.reducedCost+arc.modifiedCost;
		reducedCost = Math.floor(reducedCost*10000)/10000;
		int chargingTime = currentLabel.chargingTime;
		if(source!=dataModel.V) {
			chargingTime-=1;
			//correctness of dominance rule when there are branches on charging times
			if(chargingTime<0 && pricingProblem.branchesOnChargingTimes.isEmpty()) return null;
		}
		Label extendedLabel = new Label(source, arc.id, currentLabel.index, reducedCost, currentLabel.remainingLoad, currentLabel.remainingTime, currentLabel.remainingEnergy, chargingTime , currentLabel.unreachable, currentLabel.ng_path, currentLabel.eta, currentLabel.srcIndices);
		return extendedLabel;
	}

	/**
	 * When the CG procedure terminates, the close function is invoked. 
	 */
	@Override
	public void close() {
		for (int i = 0; i < vertices.length; i++) {
			vertices[i].processedLabels = new ArrayList<Label>(dataModel.numArcs);
			vertices[i].unprocessedLabels =  new PriorityQueue<Label>(dataModel.numArcs, new Label.SortLabels());
			vertices[i].SRCIndices = new ArrayList<>();
		}
		this.nodesToProcess = new PriorityQueue<Vertex>(new SortVertices());
	}

	/**
	 * When the CG procedure terminates, the close function is invoked. 
	 */
	public void restart() {
		for (int i = 0; i < vertices.length; i++) {
			vertices[i].processedLabels = new ArrayList<Label>(dataModel.numArcs);
			vertices[i].unprocessedLabels =  new PriorityQueue<Label>(dataModel.numArcs, new Label.SortLabels());
		}
		this.nodesToProcess = new PriorityQueue<Vertex>(new SortVertices());
	}

	/**
	 * This method produces zero or more columns. 
	 */
	@Override
	protected List<Route> generateNewColumns() {

		//Solve the problem and check the solution
		boolean existsElementaryRoute=false;
		boolean maxNeighborhoodSize=false;
		List<Route> newRoutes=new ArrayList<>(this.numCols);  			//list of routes
		List<Route> nonElementaryRoutes=new ArrayList<>(this.numCols);  //list of nonelementary routes

		/**Until finding an elementary route or reaching a max neighborhood size*/
		while(!existsElementaryRoute && !maxNeighborhoodSize) {

			this.runLabeling(); 										//runs the labeling algorithm

			if(vertices[dataModel.V].unprocessedLabels.isEmpty()) {
				existsElementaryRoute = true; pricingProblemInfeasible=true; this.objective=Double.MAX_VALUE;
			}
			else {
				this.pricingProblemInfeasible=false;
				//Bound component
				if(vertices[dataModel.V].unprocessedLabels.size()< this.numCols) {
					pricingProblem.bestReducedCost = vertices[dataModel.V].unprocessedLabels.peek().reducedCost;
					for (Label label: vertices[dataModel.V].unprocessedLabels)
						if(pricingProblem.bestReducedCost>label.reducedCost) pricingProblem.bestReducedCost = label.reducedCost;
				}
				for (Label label: vertices[dataModel.V].unprocessedLabels) {
					int departureTime = (int) (label.remainingTime/10);
					int load = dataModel.Q - label.remainingLoad;
					if (label.reducedCost<=-dataModel.precision) {		//generate new column if it has negative reduced cost
						boolean isElementary = true;
						HashMap<Integer, Integer> route=new HashMap<Integer, Integer>(dataModel.C); int cost = 0; int energy = dataModel.E-label.remainingEnergy; double reducedCost = label.reducedCost;
						ArrayList<Integer> arcs = new ArrayList<Integer>(dataModel.C);
						int initialChargingTime = dataModel.arcs[label.nextArc].head-dataModel.V; int chargingTime = 0;
						int currentVertex = label.vertex;
						while(currentVertex!=dataModel.C+1) {
							Arc currentArc = dataModel.arcs[label.nextArc];
							cost+=currentArc.cost;
							int nextVertex = currentArc.head;
							if (currentVertex>=1 && currentVertex<=dataModel.C) {
								if(route.containsKey(currentVertex)) {route.replace(currentVertex, route.get(currentVertex)+1); isElementary = false;} 
								else route.put(currentVertex, 1);
							}else if(currentVertex!=dataModel.V && currentVertex!=0) chargingTime++;

							label = vertices[nextVertex].processedLabels.get(label.nextLabelIndex);
							if(currentArc.tail>=0 && currentArc.tail<=dataModel.C) arcs.add(currentArc.id);
							currentVertex = nextVertex;
						}
						//Gets the route sequence (of customers)
						int[] routeSequence = new int[arcs.size()-1];
						int counter = 0;
						for(Integer arc: arcs) {
							if(counter>=routeSequence.length) break;
							routeSequence[counter] = dataModel.arcs[arc].head;
							counter++;
						}
						Route column = new Route("exactLabeling", false, route, routeSequence, pricingProblem, cost, departureTime, energy, load, reducedCost, arcs, initialChargingTime, chargingTime);
						if (isElementary) {existsElementaryRoute = true; newRoutes.add(column);}
						else {nonElementaryRoutes.add(column);}
					}
				}
				//Enlarge ng-sets (neighborhoods)
				if (!existsElementaryRoute) {
					maxNeighborhoodSize = !enlargeNeighborhoods(nonElementaryRoutes); 
					if(!maxNeighborhoodSize) {nonElementaryRoutes = new ArrayList<Route>();newRoutes=new ArrayList<>(); restart();} //restart //run again
					else {newRoutes = nonElementaryRoutes; existsElementaryRoute = true;}
				}
			}
		}
		close();
		return disjointBlocks(newRoutes);
	}

	/**
	 * Finds disjoint block of routes (to diversify)
	 */
	public List<Route> disjointBlocks(List<Route> newRoutes){

		if(newRoutes.isEmpty()) return newRoutes;
		Collections.sort(newRoutes, new Comparator<Route>() {
			public int compare(Route a, Route b){
				if(a.reducedCost>b.reducedCost) return 1;
				if(a.reducedCost<b.reducedCost) return -1;
				return 0;
			}
		});
		this.objective = newRoutes.get(0).reducedCost;

		//Diversify routes
		int blocks = 10;
		List<Route> disjointRoutes = new ArrayList<Route>(this.numCols);
		int[][] blocksWithCustomer = new int[dataModel.C][blocks];
		for(Route route: newRoutes) {
			for (int j = 0; j < blocks; j++) {
				int similarity = 0;
				for (int i: route.route.keySet()) {similarity+=blocksWithCustomer[i-1][j];}
				if (similarity<= similarityThreshold) {
					for (int i: route.route.keySet()) {blocksWithCustomer[i-1][j]+=1;}
					disjointRoutes.add(route);
					break;
				}
			}
		}
		return disjointRoutes;
	}

	/**
	 * This class is invoked when only nonelementary routes are found. 
	 * @return true if the maximum size per neighborhood has been reached
	 */
	public boolean enlargeNeighborhoods(List<Route> nonElementaryRoutes) {

		boolean enlarged = false;
		ArrayList<Integer> cyclingVertics = new ArrayList<Integer>();
		for(Route route:nonElementaryRoutes) {
			ArrayList<Integer> visitedCustomers = new ArrayList<Integer>(dataModel.C);
			boolean[] visited = new boolean[dataModel.C];
			for(int arc: route.arcs) {
				int head = dataModel.arcs[arc].head;
				if(head<dataModel.C) {
					visitedCustomers.add(head);
					if(visited[head-1]) { //there is a cycle
						for (int i = visitedCustomers.size()-2; i >=0; i--) {
							int node = visitedCustomers.get(i);
							if(node == head) {cyclingVertics.add(head); break;}
							else if(!dataModel.vertices[node].neighbors.contains(head) && dataModel.vertices[node].neighbors.size()<=dataModel.DeltaMax) { 
								dataModel.vertices[node].neighbors.add(head); 
								enlarged = true;
								logger.debug("Adding: " + head + " to the neighborhood of: "+node + " (size=" + dataModel.vertices[node].neighbors.size()+")");
							}
						}
					}else visited[head-1] = true;
				}
			}
		}
		return enlarged;
	}

	/**
	 * When the Pricing Problem is solved, the set objective function gets invoked first. 
	 */
	@Override
	protected void setObjective() {
		//Already done by the heuristic labeling (must be invoked first)
	}

	/**
	 * Verifies if a label is dominated. Returns true if it is, false otherwise.
	 * If the label is dominated it is discarded
	 * If the label is not dominated, the existing labels dominated by the label is discarded
	 * @param label to which check dominance
	 */
	public boolean checkDominance(Label newLabel) {

		Vertex currentVertex = vertices[newLabel.vertex];

		ArrayList<Label> labelsToDelete = new ArrayList<Label>();
		for(Label existingLabel: currentVertex.unprocessedLabels) {
			if(isDominated(existingLabel, newLabel)) labelsToDelete.add(existingLabel);
		}

		currentVertex.unprocessedLabels.removeAll(labelsToDelete);
		if(currentVertex.unprocessedLabels.isEmpty()) nodesToProcess.remove(currentVertex);

		for(Label existingLabel: currentVertex.processedLabels) {
			if(isDominated(newLabel, existingLabel))
				return true;
		}
		return false;
	}

	/**
	 * Verifies if L1 is (strongly) dominated by L2
	 * @param L1, L2 labels
	 */
	public boolean isDominated(Label L1, Label L2) {

		if(L1.vertex>dataModel.C) { //charging time vertices
			if (L2.chargingTime>L1.chargingTime) return false;
			if (L2.reducedCost-L1.reducedCost>dataModel.precision) return false;
			return true;

		}else { 					//customer vertices

			if (L1.vertex>0 && L2.remainingLoad<L1.remainingLoad) return false; 	//load
			if (L2.reducedCost-L1.reducedCost>dataModel.precision) return false; 	//reduced cost
			if (L2.remainingTime<L1.remainingTime) return false; 					//time
			if (L2.remainingEnergy<L1.remainingEnergy) return false;				 //energy

			//reducedCost
			double reducedCostL2 = 0;
			if (L1.vertex>0) {
				for(int i: L2.srcIndices) {
					if(!L1.eta[i]) {
						SubsetRowInequality src = pricingProblem.subsetRowCuts.get(i);
						if(!L2.unreachable[src.cutSet[0]-1] || !L2.unreachable[src.cutSet[1]-1] || !L2.unreachable[src.cutSet[2]-1]) {
							int dualIndex = dataModel.C+dataModel.last_charging_period+i;
							reducedCostL2+=pricingProblem.dualCosts[dualIndex];
						}
					}
					if (L2.reducedCost-reducedCostL2-L1.reducedCost>dataModel.precision) return false;
				}
			}

			if (L2.reducedCost-reducedCostL2-L1.reducedCost>dataModel.precision) return false;
			Vertex currentVertex = vertices[L1.vertex];
			if (currentVertex.id > 0) {
				for(int i: currentVertex.neighbors) {
					if(L2.ng_path[i-1] && (!L1.unreachable[i-1] && !L1.ng_path[i-1])) {
						return false;
					}
				}
			}
			return true;
		}
	}

	/**
	 * Listen to branching decisions. The pricing problem is changed by the branching decisions.
	 * @param bd BranchingDecision
	 */
	@Override
	public void branchingDecisionPerformed(BranchingDecision bd) {
		if(bd instanceof FixArc) { //Fixing one arc
			FixArc fixArcDecision = (FixArc) bd;
			for(int infeasibleArc: fixArcDecision.infeasibleArcs) this.infeasibleArcs[infeasibleArc] = true;
		}else if(bd instanceof RemoveArc) {//Removing one arc
			RemoveArc removeArcDecision= (RemoveArc) bd;
			infeasibleArcs[removeArcDecision.arc] = true;
		}
	}

	/**
	 * When the Branch-and-Price algorithm backtracks, branching decisions are reversed.
	 * @param bd BranchingDecision
	 */
	@Override
	public void branchingDecisionReversed(BranchingDecision bd) {
		if(bd instanceof FixArc) { //Fixing one arc
			FixArc fixArcDecision = (FixArc) bd;
			for(int infeasibleArc: fixArcDecision.infeasibleArcs) this.infeasibleArcs[infeasibleArc] = false;
		}else if(bd instanceof RemoveArc) {//Removing one arc
			RemoveArc removeArcDecision= (RemoveArc) bd;
			infeasibleArcs[removeArcDecision.arc] = false;
		}
	}

	/**
	 * Returns the time in seconds (and considering two decimals)
	 */
	public double getTimeInSeconds(double time) {
		double realTime = time*0.001;
		realTime = Math.floor(realTime*100)/100; //two decimals
		return realTime;
	}

	/**
	 * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
	 */
	public class SortVertices implements Comparator<Vertex> {
		@Override
		public int compare(Vertex vertex1, Vertex vertex2) {

			if(vertex2.id==0 && (vertex1.id>0 && vertex1.id<=dataModel.C)) return -1;
			if(vertex1.id==0 && (vertex2.id>0 && vertex2.id<=dataModel.C)) return 1;

			if(vertex1.id<dataModel.V && vertex2.id>=dataModel.V) return -1;
			if(vertex1.id>=dataModel.V && vertex2.id<dataModel.V) return 1;
			if(vertex1.id>=dataModel.V && vertex2.id>=dataModel.V) {
				if(vertex1.id>vertex2.id) return -1;
				else return 1;
			}

			Label L1 = vertex1.unprocessedLabels.peek();
			Label L2 = vertex2.unprocessedLabels.peek();
			if(L1.remainingLoad>L2.remainingLoad) return -1;
			if(L1.remainingLoad<L2.remainingLoad) return 1;
			if(L1.remainingEnergy>L2.remainingEnergy) return -1;
			if(L1.remainingEnergy<L2.remainingEnergy) return 1;
			if(L1.remainingTime>L2.remainingTime) return -1;
			if(L1.remainingTime<L2.remainingTime) return 1;
			if(L1.reducedCost<L2.reducedCost) return -1;
			if(L1.reducedCost>L2.reducedCost) return 1;
			return 0;
		}
	}
}