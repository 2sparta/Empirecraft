package ua.empirecraft.forgemigration;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read the political map from the archived H2 database without changing any Feudal records. */
public final class FeudalMapImport {
    private FeudalMapImport() {}

    public static void main(String[] args) throws Exception {
        Path archive = Path.of(args[0]).toAbsolutePath().normalize();
        Class.forName("org.h2.Driver");
        try (var db = DriverManager.getConnection("jdbc:h2:file:" + archive
                + ";DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE;ACCESS_MODE_DATA=r", "sa", "")) {
            var estates = new ArrayList<Map<String, Object>>();
            try (var statement = db.createStatement(); var rows = statement.executeQuery(
                    "SELECT e.id,e.name,e.kind,e.lord,COALESCE(t.parent,'') AS parent FROM estates e "
                    + "LEFT JOIN ties t ON e.id=t.child WHERE e.active=TRUE ORDER BY e.id")) {
                while (rows.next()) {
                    var row = new LinkedHashMap<String, Object>();
                    for (String key : List.of("id", "name", "kind", "lord", "parent")) {
                        row.put(key, java.util.Objects.toString(rows.getString(key), ""));
                    }
                    estates.add(row);
                }
            }
            var points = new ArrayList<Map<String, Object>>();
            try (var statement = db.createStatement(); var rows = statement.executeQuery(
                    "SELECT p.* FROM map_points p JOIN estates e ON p.estate=e.id WHERE e.active=TRUE ORDER BY p.estate,p.kind")) {
                while (rows.next()) {
                    var row = new LinkedHashMap<String, Object>();
                    var columns = rows.getMetaData();
                    for (int i = 1; i <= columns.getColumnCount(); i++) {
                        row.put(columns.getColumnLabel(i).toLowerCase(Locale.ROOT), rows.getObject(i));
                    }
                    points.add(row);
                }
            }
            try (var output = Files.newBufferedWriter(Path.of(args[1]))) {
                new Gson().toJson(Map.of("estates", estates, "points", points), output);
            }
        }
    }
}
