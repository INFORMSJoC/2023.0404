Yamín et al. 2024
Instances for the Electric Vehicle Routing and Overnight Charging Scheduling Problem on a Multigraph.
Number of instances: 112.

------------------------------------------------------------------------------------------------------------------------------

The instances are derived from the VRPTW benchmark of Solomon (http://web.cba.neu.edu/~msolomon/problems.htm). 
These instances have a 4-parameter naming convention DTm-n, where:
- D is the geographical distribution of the customers; it can be either R (random), C (clustered), or RC (mix of random and clustered). 
- T is the tightness of the time windows; it can be either 1 or 2, where instances of type 1 have tighter time windows than instances of type 2. 
- m is the (two-digit) instance number. 
- n is the number of customers. 
The n-customer instances are derived from the 100-customer instances by considering only the first n customers.

------------------------------------------------------------------------------------------------------------------------------

<info>
- <num_chargers> number of chargers available at the depot. 
- <num_arcs> number of arcs between customers or a customer and the depot. 
- <two alternative pairs> number of node pairs with two arcs between them. 
- <avg_alternatives> average number of alternatives to travel between two locations.
<\info> 
<network>
	<nodes>
	- nodes with type=0 are the depots (either the source or the sink). 
	- nodes with type=1 are customers. 
	- coordinates are given in kilometers (km). 
	- time windows <tw> are scaled by ten, given that travel times are integer values obtained by truncating the nonnegative distances with one decimal point.
	<\nodes>
	<links>
	- <travel_cost> integer travel distance. 
	- <travel_time> integer travel time (including the service time at the arcs' tail node). 
	- <energy_consumption> is given in decawatt-hours (dWh). 
	- <min_cost>, <min_time>, and <min_energy> are computed by running the all-time pairs shortest path algorithm. 
	- <is_min_cost> indicates whether the arc has the minimum distance among all arcs from that tail node to that head node. 
	- one unit of distance (1 km) is traversed in one unit of time (1 minute). 
	<\links>
<\network>
<fleet>
- routes start at node 0 (the depot source) and end at node C+1 (the depot sink), where C is the number of customers. 
- <capacity> refers to the load capacity of each EV. 
- EVs can charge between the <initial_charging_period> and until the <last_charging_period>. 
- the <energy_capacity> is given in dWh. 
- the piecewise-linear recharging function is defined by three breakpoints, plus point (0,0).
- each breakpoint is characterized by an <energy_level> in dWh and a recharging_rate in dWh/min.
- the <inverse_recharging_function> defines the number of <periods> (i.e., minutes) required to achieve a given <energy_level> in dWh.
- note: the charging scheduling is a discrete-time problem. 
<\fleet>
<requests>
- each customer has one (1) request. 
- each customer has a service time, which is already included in the arcs' travel time. 
<\requests>




 