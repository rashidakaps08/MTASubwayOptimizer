import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

public class subwayBack {

    public static class Station {
        private final String code;
        private final String name;
        private final String line;
        private final String compId;

        public Station(String code, String name, String line, String compId) {
            this.code = code;
            this.name = name;
            this.line = line;
            this.compId = compId;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getLine() { return line; }
        public String getComplexId() { return compId; }
    }

    public static class Track { 
        private final String target;
        private final int time;
        private final String lineName;

        public Track(String target, int time, String lineName) {
            this.target = target;
            this.time = time;
            this.lineName = lineName;
        }

        public String getTarget() { return target; }
        public int getTime() { return time; }
        public String getLineName() { return lineName; }
    }

    public static class DijkstraNode { 
        public final String code;
        public final int weight;
        public final String lineName;

        public DijkstraNode(String code, int weight, String lineName) {
            this.code = code; this.weight = weight; this.lineName = lineName;
        }
    }

    public static class NodeComparator implements Comparator<DijkstraNode> {
        @Override
        public int compare(DijkstraNode a, DijkstraNode b) {
            return Integer.compare(a.weight, b.weight);
        }
    }

    public static class MstEdge {
        public final String src;
        public final String tgt;
        public final int cost;

        public MstEdge(String src, String tgt, int cost) {
            this.src = src; this.tgt = tgt; this.cost = cost;
        }
    }

    public static class MstComparator implements Comparator<MstEdge> {
        @Override
        public int compare(MstEdge a, MstEdge b) {
            return Integer.compare(a.cost, b.cost);
        }
    }

    private final Map<String, Station> stations = new HashMap<>();
    private final Map<String, List<Track>> graph = new HashMap<>();

    public Collection<Station> getAllStations() { return stations.values(); }
    public Station getStation(String code) { return stations.get(code); }
    public List<Track> getTracksFrom(String code) { return graph.getOrDefault(code, new ArrayList<>()); }

    // phase 1

    public void loadFromCsv(String file) {
        Map<String, List<String>> lines = new HashMap<>(); //groups by subway line
        Map<String, List<String>> hubs = new HashMap<>(); //groups by transfer hub

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            String line;
            
            while ((line = br.readLine()) != null) {
                // split the line
                String[] cols = line.split(",", -1);
                
                if (cols.length >= 7) {
                    String c = cols[0].trim(); String n = cols[2].trim();
                    String ln = cols[4].trim(); String comp = cols[6].trim();

                    Station s = new Station(c, n, ln, comp);
                    stations.put(c, s);
                    graph.put(c, new ArrayList<>()); //creates blank of tracks so it cna be connected later

                    lines.putIfAbsent(ln, new ArrayList<>()); //temp sorting buckets
                    lines.get(ln).add(c);

                    if (!comp.isEmpty()) {
                        hubs.putIfAbsent(comp, new ArrayList<>());
                        hubs.get(comp).add(c);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading CSV: " + e.getMessage());
            return;
        }

        //connect consecutive stations on the same line
        // Connect consecutive stations on the same line
        List<String> lineKeys = new ArrayList<>(lines.keySet());
        for (int i = 0; i < lineKeys.size(); i++) {
            String l = lineKeys.get(i);
            List<String> codes = lines.get(l);
            for (int j = 0; j < codes.size() - 1; j++) {
                // FIXED: Using .get() instead of [] brackets
                String a = codes.get(j); 
                String b = codes.get(j + 1);
                
                graph.get(a).add(new Track(b, 2, l));
                graph.get(b).add(new Track(a, 2, l));
            }
        }

        // connect stations in the same complex (transfers) 
        List<List<String>> clusters = new ArrayList<>(hubs.values());
        for (int i = 0; i < clusters.size(); i++) {
            List<String> cluster = clusters.get(i);
            for (int j = 0; j < cluster.size(); j++) {
                for (int k = 0; k < cluster.size(); k++) {
                    String nA = cluster.get(j); String nB = cluster.get(k);
                    if (!nA.equals(nB)) {
                        // kinda ugly loop but it does the job
                        graph.get(nA).add(new Track(nB, 4, "TRANSFER"));
                    }
                }
            }
        }
        System.out.println("Graph Build Complete: Loaded " + stations.size() + " stations successfully.");
    }

    public List<String> findShortestPath(String start, String end) {
        PriorityQueue<DijkstraNode> pq = new PriorityQueue<>(new NodeComparator());
        Map<String, Integer> dists = new HashMap<>();
        Map<String, String> prev = new HashMap<>();

        List<String> all = new ArrayList<>(stations.keySet());
        for (int i = 0; i < all.size(); i++) {
            dists.put(all.get(i), Integer.MAX_VALUE);
        }

        dists.put(start, 0);
        pq.add(new DijkstraNode(start, 0, ""));

        while (!pq.isEmpty()) {
            DijkstraNode curr = pq.poll();

            if (curr.code.equals(end)) break;
            if (curr.weight > dists.get(curr.code)) continue;

            List<Track> edges = getTracksFrom(curr.code);
            for (int i = 0; i < edges.size(); i++) {
                Track e = edges.get(i);
                int pen = 0;
                
                //penalty if changing lines
                if (!curr.lineName.isEmpty() && !curr.lineName.equals("TRANSFER") && 
                    !e.getLineName().equals("TRANSFER") && !curr.lineName.equals(e.getLineName())) {
                    pen = 5;
                }

                int newDist = curr.weight + e.getTime() + pen;

                if (newDist < dists.get(e.getTarget())) {
                    dists.put(e.getTarget(), newDist);
                    prev.put(e.getTarget(), curr.code);
                    pq.add(new DijkstraNode(e.getTarget(), newDist, e.getLineName()));
                }
            }
        }

        List<String> path = new ArrayList<>();
        String c = end;
        
        if (!prev.containsKey(c) && !c.equals(start)) return path;

        while (c != null) {
            path.add(c);
            c = prev.get(c);
        }

        Collections.reverse(path);
        return path;
    }

    // phase 2

    public Map<String, Integer> calculateDegreeCentrality() {
        Map<String, Integer> deg = new HashMap<>();
        List<String> codes = new ArrayList<>(stations.keySet());
        for (int i = 0; i < codes.size(); i++) {
            String c = codes.get(i);
            deg.put(c, getTracksFrom(c).size());
        }
        return deg;
    }

    public Map<String, Double> calculateBetweennessCentrality() {
        Map<String, Double> bw = new HashMap<>();
        List<String> nodes = new ArrayList<>(stations.keySet());

        for (String code : nodes) bw.put(code, 0.0);

        // this takes forever to run
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                List<String> path = findShortestPath(nodes.get(i), nodes.get(j));
                for (int k = 1; k < path.size() - 1; k++) {
                    String inter = path.get(k);
                    bw.put(inter, bw.get(inter) + 1.0);
                }
            }
        }
        return bw;
    }

    public List<MstEdge> calculateMinimumSpanningTree() {
        List<MstEdge> mst = new ArrayList<>();
        if (stations.isEmpty()) return mst;

        Set<String> vis = new HashSet<>();
        PriorityQueue<MstEdge> pq = new PriorityQueue<>(new MstComparator());

        // grab the first one
        String start = stations.keySet().iterator().next();
        vis.add(start);

        for (Track t : getTracksFrom(start)) {
            pq.add(new MstEdge(start, t.getTarget(), t.getTime()));
        }

        while (!pq.isEmpty() && vis.size() < stations.size()) {
            MstEdge edge = pq.poll();

            if (!vis.contains(edge.tgt)) {
                vis.add(edge.tgt);
                mst.add(edge);

                for (Track t : getTracksFrom(edge.tgt)) {
                    if (!vis.contains(t.getTarget())) {
                        pq.add(new MstEdge(edge.tgt, t.getTarget(), t.getTime()));
                    }
                }
            }
        }
        return mst;
    }


    

    public static void main(String[] args) {
        subwayBack backend = new subwayBack();
        backend.loadFromCsv("stations.csv");

        if (backend.getAllStations().isEmpty()) {
            System.out.println(" CSV failed to load. Check file location!");
            return;
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8095), 0);

            server.createContext("/", exchange -> {
                StringBuilder htmlBuilder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader("index.html"))) {
                    String fileLine;
                    while ((fileLine = reader.readLine()) != null) {
                        htmlBuilder.append(fileLine).append("\n");
                    }
                } catch (IOException e) {
                    String errorMsg = "<h1>Error: missing index.html file in project directory!</h1>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(404, errorMsg.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(errorMsg.getBytes()); }
                    return;
                }

                String htmlResponse = htmlBuilder.toString();
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, htmlResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(htmlResponse.getBytes()); }
            });

            server.createContext("/api/route", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String start = ""; String end = "";
                
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length > 1) {
                            if (pair[0].equals("start")) start = pair[1];
                            if (pair[0].equals("end")) end = pair[1];
                        }
                    }
                }

                List<String> route = backend.findShortestPath(start, end);
                StringBuilder responseText = new StringBuilder();

                if (route.isEmpty()) {
                    responseText.append("❌ No path found between station codes '").append(start).append("' and '").append(end).append("'.");
                } else {
                    responseText.append("📍 OPTIMAL ROUTE FOUND (").append(route.size()).append(" Stops):\n\n");
                    for (int i = 0; i < route.size(); i++) {
                        Station s = backend.getStation(route.get(i));
                        if (s != null) {
                            if (i == 0) responseText.append("[BOARD] ");
                            else if (i == route.size() - 1) responseText.append("[ARRIVE] ");
                            else responseText.append("   ↳  ");
                            
                            responseText.append(s.getName()).append(" (").append(s.getLine()).append(" Line)\n");
                        }
                    }
                }

                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                byte[] bytes = responseText.toString().getBytes("UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            });

    server.createContext("/api/stations", exchange -> {
    StringBuilder json = new StringBuilder("{");
    boolean first = true;
    for (Station s : backend.getAllStations()) {
        if (!first) json.append(",");
        String safeName = s.getName().replace("\"", "\\\"");
        json.append("\"").append(s.getCode()).append("\":{")
            .append("\"name\":\"").append(safeName).append("\",")
            .append("\"line\":\"").append(s.getLine()).append("\"}");
        first = false;
    }
    json.append("}");
    byte[] bytes = json.toString().getBytes("UTF-8");
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
});

            

            server.start();
            System.out.println("🛰️ Subway Web UI Server live at: http://localhost:8095");

        } catch (IOException e) {
            System.out.println("Failed to start server: " + e.getMessage());
        }
    }

}   


