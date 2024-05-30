package model;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jorlib.frameworks.columnGeneration.model.ModelInterface;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import columnGeneration.Label;

/**
 * The Electric Vehicle Routing and Overnight Charging Scheduling Problem on a Multigraph
 * @author Daniel Yamín (Universidad de los Andes)
 */

public final class EVRPTW implements ModelInterface {

	public final String instanceName;						//instance name

	//Basic information
	public int C; 											//number of customers
	public int Q; 											//load capacity
	public int V; 											//number of vertices (customer-based graph)
	public DirectedWeightedMultigraph<Integer, Arc> graph; 	//graph of the problem
	public Vertex[] vertices; 								//set of vertices
	public Arc[] arcs; 										//set of arcs
	public int numArcs; 									//number of arcs
	public int numArcsRoadNetwork; 							//number of arcs in the road network
	public int numVertices; 								//number of vertices
	public int twoAlternativesPairs; 						//number of node pairs with two alternatives to travel
	public double avgAlternatives; 							//avg. number of alternatives to travel

	//Energy information
	public int B; 											//number of chargers
	public int E; 											//energy capacity
	public int T_min; 										//opening time of the depot
	public int last_charging_period; 						//charging end time
	public int[] f_inverse; 								//(inverse) recharging function

	//Acceleration strategies and BPC
	public final int Delta; 								//number of neighbors (ng-path).
	public final int DeltaMax; 								//maximum neighborhood size
	public final double precision = 0.09; 					//precision for the column generation algorithm (it is scaled by 10)
	public long exactPricingTime = 0; 						//time spent on the exact labeling algorithm
	public long heuristicPricingTime = 0; 					//time spent on the heuristic labeling algorithm
	public int columnsRootNode = 0; 						//columns generated at the root node
	public int cutsRootNode = 0; 							//cuts separated at the root node
	public boolean[] infeasibleArcs; 						//infeasible arcs in the pricing problem


	/**
	 * Constructs a new mE-VRSPTW instance. 
	 * @param instanceName input instance.
	 * @throws IOException Throws IO exception when the instance cannot be found.
	 */
	public EVRPTW(String instanceName) throws IOException {
		this.instanceName = instanceName;
		this.Delta = (instanceName.substring(0, 2).equals("R1") || instanceName.substring(0, 2).equals("C1") || 
				instanceName.substring(0, 3).equals("RC1")) ? 7 : 12;
		this.DeltaMax = Delta+5;
		this.C = Integer.parseInt(instanceName.substring(Math.max(instanceName.length() - 2, 0)));
		this.V = C+2;
		this.graph = new DirectedWeightedMultigraph<Integer, EVRPTW.Arc>(Arc.class);
		this.numArcs = 0;

		//create a new file output stream.
		PrintStream fileOut = new PrintStream("./results/log/"+this.getName()+".log");
		System.setOut(fileOut);

		//read the instance
		readData();
		System.out.println(" - Number of chargers: " + this.B);
		System.out.println(" - Energy capacity: " + this.E);
		System.out.println(" - Full recharging time: " + this.f_inverse[this.E]);
	}

	/** Name of the current instance */
	@Override
	public String getName() {
		return instanceName;
	}

	/**
	 * Reads an instance.
	 * The file must be stored in ./data/instances and following the guidelines of the VRPREP. 
	 */
	private void readData() {
		try {

			/** Reading input file **/
			System.out.println(" - ================ LOADING INSTANCE ================");
			System.out.println(" - Instance: " + this.getName());

			File xmlFile = new File("./data/"+this.getName()+".xml");
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(xmlFile);

			//Load initial information
			loadInitialInformation(doc);

			//we need T_tilde first
			Element vehicleProfileElement = (Element) doc.getElementsByTagName("vehicle_profile").item(0);
			Element customElements = (Element) vehicleProfileElement.getElementsByTagName("custom").item(0);
			this.last_charging_period = Integer.parseInt(customElements.getElementsByTagName("last_charging_period").item(0).getTextContent())+1;

			//Load vertices
			this.numVertices = V+last_charging_period+1;
			this.vertices = new Vertex[numVertices];
			loadVertices(doc);
			this.T_min = vertices[0].opening_tw;

			//Load arcs
			int auxNumArcs = 2*(V*V-V)+3*last_charging_period;
			arcs = new Arc[auxNumArcs];
			loadArcs(doc);

			//Load fleet (vehicle profile and charging information)
			loadFleet(doc);

			/** Neighborhoods (ng-path). **/
			for (int i = 1; i <= this.C; i++) {

				//Sort arcs
				ArrayList<Arc> incoming = new ArrayList<Arc>(graph.incomingEdgesOf(i));
				Collections.sort(incoming, new SortByCost());
				ArrayList<Arc> outgoing = new ArrayList<Arc>(graph.outgoingEdgesOf(i));
				Collections.sort(outgoing, new SortByCost());

				int outgoingIndex = 0;
				int incomingIndex = 0;
				boolean processOutgoing = true;
				while(true) {
					if((outgoingIndex>=outgoing.size() && incomingIndex>=incoming.size()) || vertices[i].neighbors.size()>=this.Delta) break;
					else if(outgoingIndex>=outgoing.size()) processOutgoing = false;
					else if(incomingIndex>=incoming.size()) processOutgoing = true;
					else if (outgoing.get(outgoingIndex).cost<incoming.get(incomingIndex).cost) {
						processOutgoing=true;
					}else {processOutgoing = false;}
					int nextCustomer = (processOutgoing) ? outgoing.get(outgoingIndex).head: incoming.get(incomingIndex).tail;
					if (nextCustomer!=0 && nextCustomer!=C+1 && !vertices[i].neighbors.contains(nextCustomer)) {
						vertices[i].neighbors.add(nextCustomer);
					}
					if(processOutgoing) outgoingIndex++;
					else incomingIndex++;
				}
				vertices[i].neighbors.add(i); //does not count for the Delta
			}

			/** (A priori) unreachable customers **/
			for (int i = 1; i <= this.C; i++) {
				for (int j = 1; j <= this.C; j++) {
					if(i!=j && !graph.containsEdge(i, j))
						vertices[j].unreachable.add(i); //backward labeling
				}
			}

			/** Charging time vertices **/
			graph.addVertex(this.V); //fictitious source
			vertices[this.V] = new Vertex(this.V, 0, vertices[0].opening_tw, vertices[0].closing_tw);

			for (int t = last_charging_period; t >= 1; t--) {
				vertices[this.V+t] = new Vertex(this.V+t, 0, vertices[0].opening_tw, vertices[0].closing_tw);
				graph.addVertex(this.V+t);
				Arc newArc = new Arc(this.numArcs, this.V+t, 0, 0, 0, 0, 0); //arc to the depot source
				graph.addEdge(this.V+t, 0, newArc); arcs[this.numArcs] = newArc; this.numArcs++;
				newArc = new Arc(this.numArcs, this.V, this.V+t, 0, 0, 0, 0); //arc from the fictitious source
				graph.addEdge(this.V, this.V+t, newArc); arcs[this.numArcs] = newArc; this.numArcs++;
				if(t<last_charging_period) {
					newArc = new Arc(this.numArcs, this.V+t, this.V+t+1, 0, 0, 0, 0); //arc to the subsequent timestep
					graph.addEdge(this.V+t, this.V+t+1, newArc); arcs[this.numArcs] = newArc; this.numArcs++;
				}
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** Defines some parameters (according to the information in the .xml file). */
	public void loadInitialInformation(Document doc) {
		Element infoElement = (Element) doc.getElementsByTagName("info").item(0);
		//number of chargers, number of arcs, two alternatives pairs, and average number of alternatives
		this.B = Integer.parseInt(infoElement.getElementsByTagName("num_chargers").item(0).getTextContent());
		this.numArcsRoadNetwork = Integer.parseInt(infoElement.getElementsByTagName("num_arcs").item(0).getTextContent());
		this.twoAlternativesPairs = Integer.parseInt(infoElement.getElementsByTagName("two_alternatives_pairs").item(0).getTextContent());
		this.avgAlternatives = Double.parseDouble(infoElement.getElementsByTagName("avg_alternatives").item(0).getTextContent());
	}

	/** Defines the vertices (according to the information in the .xml file) */
	public void loadVertices(Document doc) {

		NodeList nodeNodes = doc.getElementsByTagName("node");
		for (int i = 0; i < nodeNodes.getLength(); i++) {

			Node node = nodeNodes.item(i);
			Element nodeElement = (Element) node;

			//id, coordx, coordy, and load
			int id = Integer.parseInt(nodeElement.getAttribute("id"));
			int coordx = Integer.parseInt(nodeElement.getElementsByTagName("cx").item(0).getTextContent());
			int coordy = Integer.parseInt(nodeElement.getElementsByTagName("cy").item(0).getTextContent());
			int load = Integer.parseInt(nodeElement.getElementsByTagName("load").item(0).getTextContent());

			//time windows
			Element timeWindowsElement = (Element) nodeElement.getElementsByTagName("tw").item(0);
			int opening_tw = Integer.parseInt(timeWindowsElement.getElementsByTagName("start").item(0).getTextContent());
			int closing_tw = Integer.parseInt(timeWindowsElement.getElementsByTagName("end").item(0).getTextContent());

			vertices[id] = new Vertex(id, coordx, coordy, load, opening_tw, closing_tw);
			graph.addVertex(id);
		}
	}


	/** Defines the arcs (according to the information in the .xml file). */
	public void loadArcs(Document doc) {

		NodeList linkNodes = doc.getElementsByTagName("link");
		this.numArcsRoadNetwork = linkNodes.getLength();

		for (int i = 0; i < linkNodes.getLength(); i++) {
			this.numArcs++;
			Node link = linkNodes.item(i);
			Element linkElement = (Element) link;

			//id, head and tail
			int id = Integer.parseInt(linkElement.getAttribute("id"));
			int tail = Integer.parseInt(linkElement.getAttribute("tail"));
			int head = Integer.parseInt(linkElement.getAttribute("head"));

			//travel cost and travel time
			int cost = Integer.parseInt(linkElement.getElementsByTagName("travel_cost").item(0).getTextContent());
			int time = Integer.parseInt(linkElement.getElementsByTagName("travel_time").item(0).getTextContent());

			//custom elements (energy and minimum values)
			Element customElements = (Element) linkElement.getElementsByTagName("custom").item(0);
			int energy = Integer.parseInt(customElements.getElementsByTagName("energy_consumption").item(0).getTextContent());
			int minimumCost = Integer.parseInt(customElements.getElementsByTagName("min_cost").item(0).getTextContent());
			int minimumTime = Integer.parseInt(customElements.getElementsByTagName("min_time").item(0).getTextContent());
			int minimumEnergy = Integer.parseInt(customElements.getElementsByTagName("min_energy").item(0).getTextContent());
			boolean minCostAlternative = Boolean.parseBoolean(customElements.getElementsByTagName("is_min_cost").item(0).getTextContent());

			Arc newArc = new Arc(id, tail, head, cost, time, energy, minimumCost, minimumTime,
					minimumEnergy, minCostAlternative);
			arcs[id] = newArc;
			graph.addEdge(tail, head, newArc);
		}
	}

	/** Defines the arcs (according to the information in the .xml file). */
	public void loadFleet(Document doc) {

		Element vehicleProfileElement = (Element) doc.getElementsByTagName("vehicle_profile").item(0);
		//vehicles' load capacity
		this.Q = Integer.parseInt(vehicleProfileElement.getElementsByTagName("capacity").item(0).getTextContent());

		//custom elements (last charging time and energy capacity)
		Element customElements = (Element) vehicleProfileElement.getElementsByTagName("custom").item(0);
		//		this.T_tilde = Integer.parseInt(customElements.getElementsByTagName("last_charging_time").item(0).getTextContent())+1;
		this.E = Integer.parseInt(customElements.getElementsByTagName("energy_capacity").item(0).getTextContent());

		//inverse recharging function
		this.f_inverse= new int[E+1]; //piecewise-linear function (rounded up)
		Element inverseChargingFunctionElement = (Element) customElements.getElementsByTagName("inverse_recharging_function").item(0);
		NodeList breakpointNodes = inverseChargingFunctionElement.getElementsByTagName("breakpoint");
		for (int i = 0; i < breakpointNodes.getLength(); i++) {
			Node breakpoint = breakpointNodes.item(i);
			Element breakpointElement = (Element) breakpoint;
			int energyLevel = Integer.parseInt(breakpointElement.getElementsByTagName("energy_level").item(0).getTextContent());
			int timesteps = Integer.parseInt(breakpointElement.getElementsByTagName("periods").item(0).getTextContent());
			f_inverse[energyLevel] = timesteps;
		}


	}
	/** Class that represents a vertex. */
	public class Vertex{

		public int id; 									//vertex id
		public int xcoord; 								//x-coordinate
		public int ycoord; 								//y-coordinate
		public int load; 								//load of the vertex
		public int opening_tw; 							//opening time window
		public int closing_tw; 							//closing time window
		public HashSet<Integer> unreachable; 			//(a priori) unreachable customers from this vertex
		public HashSet<Integer> neighbors; 				//neighbors of the vertex 
		public ArrayList<Integer> SRCIndices; 			//indices of the SRC containing this vertex
		public ArrayList<Label> processedLabels; 		//labels that have reached the vertex and are non-dominated
		public PriorityQueue<Label> unprocessedLabels; 	//labels that have reached the vertex but have not yet been processed


		/**
		 * Creates a new (customer) vertex.
		 * @throws IOException Throws IO exception when the instance cannot be found.
		 */
		public Vertex(int id, int xcoord, int ycoord, int load, int opening_tw, int closing_tw) {
			this.id = id;
			this.xcoord = xcoord;
			this.ycoord = ycoord;
			this.load = load;
			this.opening_tw = opening_tw;
			this.closing_tw = closing_tw;
			this.unreachable = new HashSet<Integer>(C);
			int auxNumArcs = 2*(V*V-V);
			this.processedLabels = new ArrayList<Label>(auxNumArcs);
			this.unprocessedLabels = new PriorityQueue<Label>(auxNumArcs, new Label.SortLabels());
			this.SRCIndices = new ArrayList<>();
			this.neighbors = new HashSet<Integer>(C);
		}


		/**
		 * Creates a new (charging time) vertex.
		 * @throws IOException Throws IO exception when the instance cannot be found.
		 */
		public Vertex(int id, int load, int opening_tw, int closing_tw) {
			this.id = id;
			this.load = load;
			this.opening_tw = opening_tw;
			this.closing_tw = closing_tw;
			int auxNumArcs = 2*(V*V-V);
			this.processedLabels = new ArrayList<Label>(auxNumArcs);
			this.unprocessedLabels = new PriorityQueue<Label>(auxNumArcs, new Label.SortLabels());
		}

		/**
		 * Obtains the string of a vertex
		 */
		@Override
		public String toString(){
			return "" + id;
		}
	}

	/** Class that represents an arc. */
	public class Arc extends DefaultWeightedEdge{

		private static final long serialVersionUID = 1L;
		public int tail; 							//tail vertex of the arc
		public int head; 							//head vertex of the arc
		public int id; 								//arc id
		public int cost; 							//cost of the arc
		public int time; 							//time of the arc
		public int energy; 							//energy of the arc
		public int minimiumCost; 					//minimum cost (multigraph representation)
		public int minimumTime; 					//minimum time (multigraph representation)
		public int minimumEnergy; 					//minimum energy (multigraph representation)
		public boolean minCostAlternative; 			//indicates if is the minimum cost alternative
		public double modifiedCost; 				//modified cost of the arc

		/**
		 * Creates a new arc.
		 * @throws IOException Throws IO exception when the instance cannot be found.
		 */
		public Arc(int id, int tail, int head, int cost, int time, int energy, int minimiumCost, int minimumTime,
				int minimumEnergy, boolean minCostAlternative) {
			this.id = id;
			this.tail = tail;
			this.head = head;
			this.cost = cost;
			this.time = time;
			this.energy = energy;
			this.minimiumCost = minimiumCost;
			this.minimumTime = minimumTime;
			this.minimumEnergy = minimumEnergy;
			this.minCostAlternative = minCostAlternative;
			this.modifiedCost = 0.0;
		}

		public Arc(int id, int tail, int head, int cost, int time, int energy, double modifiedCost) {
			this.id = id;
			this.tail = tail;
			this.head = head;
			this.cost = cost;
			this.time = time;
			this.energy = energy;
			this.modifiedCost = modifiedCost;
		}

		/** Obtains the string representation of the arc. */
		@Override
		public String toString(){
			return "("+tail+","+head+"); " + this.id;
		}

		@Override
		public int hashCode() {
			// TODO Auto-generated method stub
			return id;
		}
	}

	/**
	 * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
	 */
	public class SortByCost implements Comparator<Arc> {
		@Override
		public int compare(Arc a1, Arc a2) {
			if(a1.cost<a2.cost) return -1;
			if(a1.cost>a2.cost) return 1;
			return 0;
		}
	}

}