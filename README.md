# NYC MTA Subway Route Optimizer

This project is an attempt to optimize the complicated MTA subway system into a streamlined process with a responsive web interface. The core objective of this system is to ingest real data from New York City transit datasets, model the underlying infrastructure as an optimized graph network, and compute the absolute fastest travel path between station platforms.

Instead of using simple pathfinding, we implemented features to help mimic real-world traits, including fixed travel time between stations, transfer delays, and penalties whenever a passenger changes physical train lines.

## How Routing Works

We modeled the subway map as a weighted graph, with nodes representing stations and edges representing tracks between stations. This network is stored as an adjacency list so we can keep track of each station's immediate neighbors.

- Neighboring stations on the same line are automatically linked together with a fixed **2-minute travel time**
- Stations within the same physical station hub get a **4-minute transfer penalty** to simulate the walk through a transfer tunnel
- We use **Dijkstra's Algorithm** to find the shortest path
- A **5-minute penalty** is applied whenever the algorithm switches from one train line to another outside of a designated transfer tunnel, so it doesn't constantly suggest hopping on and off at every stop

## Station Importance Analysis

Three tools are used to analyze how important a given station is to the overall network:

1. **Degree centrality** — counts how many tracks connect directly to a station. Higher numbers indicate a major intersection hub.
2. **Betweenness centrality** — calculates the shortest path between every possible pair of stations in NYC and counts how many times each station shows up along the way. High scorers are the most heavily-used stations.
3. **Minimum Spanning Tree (MST)** — uses Prim's Algorithm to find the shortest possible set of tracks that connects every station in the city with no loops.

## The Web Interface

- A local web server runs on **port 8080** via `subwayBack.java`
- Visiting the page loads `index.html`, which serves as the front end
- On load, the front end downloads the full list of stations as a JSON object and stores it in browser memory, which powers the autocomplete in the station drop-downs
- Clicking **"Find Optimal Path"** sends the chosen station IDs to the Java server, which runs Dijkstra's algorithm and returns the result
- The result is rendered using the official MTA line hex color codes (`mtaColors`), displayed as a visual trip timeline with an estimated total trip time at the top

## Tech Stack

- **Backend:** Java (`com.sun.net.httpserver.HttpServer`)
- **Frontend:** HTML/JS, JSON-driven
- **Data:** NYC transit station data (`stations.csv`)

## Running It

1. Make sure you have a JDK installed
2. Compile and run the server:
   ```
   javac subwayBack.java
   java subwayBack
   ```
3. Open your browser to `http://localhost:8080`
4. Select your starting and ending stations from the drop-downs and click **Find Optimal Path**
