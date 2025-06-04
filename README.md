[![INFORMS Journal on Computing Logo](https://INFORMSJoC.github.io/logos/INFORMS_Journal_on_Computing_Header.jpg)](https://pubsonline.informs.org/journal/ijoc)

# The Electric Vehicle Routing and Overnight Charging Scheduling Problem on a Multigraph

This archive is distributed in association with the [INFORMS Journal on
Computing](https://pubsonline.informs.org/journal/ijoc) under the [MIT License](LICENSE).

The software and data in this repository are a snapshot of the software and data
that were used in the research reported on in the paper 
[The Electric Vehicle Routing and Overnight Charging Scheduling Problem on a Multigraph](https://doi.org/10.1287/ijoc.2023.0404) by Daniel Yamín, Guy Desaulniers, and Jorge E. Mendoza.

**Important: This code is being developed on an on-going basis at https://github.com/danielyamin97/BPC-mE-VRSPTW.
Please go there if you would like to get a more recent version or would like support.**

## Cite

To cite the contents of this repository, please cite both the paper and this repo, using their respective DOIs.

https://doi.org/10.1287/ijoc.2023.0404



https://doi.org/10.1287/ijoc.2023.0404.cd

Below is the BibTex for citing this snapshot of the repository.

```
@misc{yamin2024electric,
  author =        {Yam{\'i}n, Daniel and Desaulniers, Guy and Mendoza, Jorge E},
  publisher =     {INFORMS Journal on Computing},
  title =         {{The Electric Vehicle Routing and Overnight Charging Scheduling Problem on a Multigraph}},
  year =          {2024},
  doi =           {10.1287/ijoc.2023.0404.cd},
  url =           {https://github.com/INFORMSJoC/2023.0404},
  note =          {Available for download at https://github.com/INFORMSJoC/2023.0404},
}  
```

## Description

This repository provides the data for the problem (mE-VRSPTW) and the code for the solution method (a BPC algorithm). The main folders are `data`, `results`, and `src`.

* The `data` folder contains the mE-VRSPTW instances used in the paper, the format of which is explained in a README file. 
* The `results` folder contains:
  * The `log` folder where information is printed throughout the algorithm's execution. 
  * The `solution` folder that contains the solution file for every instance of the mE-VRSPTW. 
  * The `output.txt` file where key information is reported after the code is executed. 
* The `src` folder contains the source code.

## Dependencies

The following two external (referenced) libraries are required to run this code:

**1. CPLEX**: It can be downloaded from [IBM CPLEX](https://www.ibm.com/docs/nl/icos/20.1.0?topic=cplex-installing); for academics, visit [IBM CPLEX Academic](https://www.ibm.com/academic/home). When using Eclipse as IDE, follow these steps: [Setting Up Eclipse for CPLEX Java API](https://www.ibm.com/docs/nl/icos/20.1.0?topic=cplex-setting-up-eclipse-java-api).

**2. jORLib**: For guidelines and instructions, visit the [jORLib GitHub Repository](https://github.com/coin-or/jorlib/tree/master) and the [Release Page](https://github.com/coin-or/jorlib/releases).


**Important: This project was coded using IBM CPLEX solver version 22.1 and jORLib branch-and-price framework version 1.1.1.**


## Data

The dataset used for the numerical study in Section 5.2 is available in the `data` folder. A README file explains the instances' file format.

## Reproducing the Numerical Results

To reproduce each result in Section 5.2. of the paper (i.e., the Electronic Companion EC.1), please run the program with the corresponding instance name passed as an argument in the `src` directory. For example, to run instance R101-25, one would pass the following argument:

```
R101-25
```

**Important: This repository is intended to reproduce the results reported in Section 5.2 of the paper (EC.1).**

## Setup

To setup and run the project in Eclipse, follow these steps:

**1. Install JDK:**

- Download and install the Java Development Kit (JDK) from [Oracle](https://www.oracle.com/java/technologies/javase-downloads.html).

**2. Install Eclipse IDE:**

- Download and install Eclipse IDE from the [official website](https://www.eclipse.org/downloads/).

**3. Import the Project:**

- Open Eclipse and select `File > Import...`
- Choose `Existing Projects into Workspace` under `General`
- Click `Next` and then `Browse...` to locate the project directory
- Select the project and click `Finish`

**4. Add Dependencies:**

- Ensure CPLEX and jORLib jar files are included in the project's build path.

**5. Run the Project:**

- Right-click the `src` directory in the Project Explorer.
- Select `Run As > Run Configurations... > Arguments`. Put the instance name (e.g., R101-25) in the program arguments box.


## Support

For support in using this software, submit an
[issue](https://github.com/INFORMSJoC/2023.0404/issues/new).
