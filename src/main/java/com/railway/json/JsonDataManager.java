package com.railway.json;

import com.railway.db.DatabaseManager;
import com.railway.model.RouteEdge;
import com.railway.model.ScheduleEntry;
import com.railway.model.Station;
import com.railway.model.Train;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the railway network (stations, track segments, trains
 * and schedules) as a single JSON document. Used both to seed a fresh
 * database with sample data and to let the user import/export their own
 * network definitions from the UI.
 * <p>
 * Expected JSON shape:
 * <pre>
 * {
 *   "stations": [ { "code": "DHK", "name": "Dhaka", "x": 420, "y": 260 }, ... ],
 *   "edges":    [ { "from": "DHK", "to": "CTG", "distanceKm": 264,
 *                    "travelTimeMinutes": 300, "baseFare": 450 }, ... ],
 *   "trains": [
 *     { "number": "701", "name": "Subarna Express",
 *       "stops": [
 *         { "station": "DHK", "seq": 1, "departureTime": "07:00" },
 *         { "station": "CTG", "seq": 2, "arrivalTime": "12:20" }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
public class JsonDataManager {

    /** Parses the JSON file and inserts everything into the database. Call off the UI thread. */
    public void importNetwork(Path jsonFile, DatabaseManager db) throws IOException, SQLException {
        String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(content);

        Map<String, Integer> codeToId = new HashMap<>();

        JSONArray stations = root.optJSONArray("stations");
        if (stations != null) {
            for (int i = 0; i < stations.length(); i++) {
                JSONObject s = stations.getJSONObject(i);
                String code = s.getString("code");
                String name = s.getString("name");
                double x = s.optDouble("x", 0);
                double y = s.optDouble("y", 0);
                int id = db.insertStation(code, name, x, y);
                codeToId.put(code, id);
            }
        }

        JSONArray edges = root.optJSONArray("edges");
        if (edges != null) {
            for (int i = 0; i < edges.length(); i++) {
                JSONObject e = edges.getJSONObject(i);
                Integer fromId = codeToId.get(e.getString("from"));
                Integer toId = codeToId.get(e.getString("to"));
                if (fromId == null || toId == null) continue;
                double distanceKm = e.getDouble("distanceKm");
                int travelTimeMinutes = e.getInt("travelTimeMinutes");
                double baseFare = e.getDouble("baseFare");
                boolean bidirectional = e.optBoolean("bidirectional", true);
                if (bidirectional) {
                    db.insertBidirectionalEdge(fromId, toId, distanceKm, travelTimeMinutes, baseFare);
                } else {
                    db.insertEdge(fromId, toId, distanceKm, travelTimeMinutes, baseFare);
                }
            }
        }

        JSONArray trains = root.optJSONArray("trains");
        if (trains != null) {
            for (int i = 0; i < trains.length(); i++) {
                JSONObject t = trains.getJSONObject(i);
                int trainId = db.insertTrain(t.getString("number"), t.getString("name"));
                JSONArray stops = t.optJSONArray("stops");
                if (stops != null) {
                    for (int j = 0; j < stops.length(); j++) {
                        JSONObject stop = stops.getJSONObject(j);
                        Integer stationId = codeToId.get(stop.getString("station"));
                        if (stationId == null) continue;
                        int seq = stop.getInt("seq");
                        String arr = stop.optString("arrivalTime", null);
                        String dep = stop.optString("departureTime", null);
                        db.insertScheduleEntry(trainId, stationId, seq, arr, dep);
                    }
                }
            }
        }
    }

    /** Reads the current database contents and writes them out as a JSON file. Call off the UI thread. */
    public void exportNetwork(Path jsonFile, DatabaseManager db) throws IOException, SQLException {
        JSONObject root = new JSONObject();
        Map<Integer, Station> stationById = new HashMap<>();

        JSONArray stationsArr = new JSONArray();
        for (Station s : db.getAllStations()) {
            stationById.put(s.getId(), s);
            JSONObject obj = new JSONObject();
            obj.put("code", s.getCode());
            obj.put("name", s.getName());
            obj.put("x", s.getX());
            obj.put("y", s.getY());
            stationsArr.put(obj);
        }
        root.put("stations", stationsArr);

        JSONArray edgesArr = new JSONArray();
        for (RouteEdge e : db.getAllEdges()) {
            Station from = stationById.get(e.getFromStationId());
            Station to = stationById.get(e.getToStationId());
            if (from == null || to == null) continue;
            JSONObject obj = new JSONObject();
            obj.put("from", from.getCode());
            obj.put("to", to.getCode());
            obj.put("distanceKm", e.getDistanceKm());
            obj.put("travelTimeMinutes", e.getTravelTimeMinutes());
            obj.put("baseFare", e.getBaseFare());
            obj.put("bidirectional", false); // edges are already expanded both ways in the DB
            edgesArr.put(obj);
        }
        root.put("edges", edgesArr);

        JSONArray trainsArr = new JSONArray();
        List<Train> trains = db.getAllTrains();
        for (Train t : trains) {
            JSONObject trainObj = new JSONObject();
            trainObj.put("number", t.getNumber());
            trainObj.put("name", t.getName());

            JSONArray stopsArr = new JSONArray();
            for (ScheduleEntry entry : db.getScheduleForTrain(t.getId())) {
                Station st = stationById.get(entry.getStationId());
                if (st == null) continue;
                JSONObject stopObj = new JSONObject();
                stopObj.put("station", st.getCode());
                stopObj.put("seq", entry.getStopSequence());
                if (entry.getArrivalTime() != null) stopObj.put("arrivalTime", entry.getArrivalTime());
                if (entry.getDepartureTime() != null) stopObj.put("departureTime", entry.getDepartureTime());
                stopsArr.put(stopObj);
            }
            trainObj.put("stops", stopsArr);
            trainsArr.put(trainObj);
        }
        root.put("trains", trainsArr);

        Files.writeString(jsonFile, root.toString(2), StandardCharsets.UTF_8);
    }
}
