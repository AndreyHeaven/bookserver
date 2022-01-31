package db.migration;

import com.fasterxml.jackson.databind.*;
import org.flywaydb.core.api.migration.*;
import java.io.*;
import java.sql.*;
import java.util.*;

public class V00003__initial_genres_data extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        final Connection connection = context.getConnection();
        InputStream json = getClass().getResourceAsStream("/db/migration/genres.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(json);
        final Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> next = fields.next();
            final String parent = UUID.randomUUID().toString();
            try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO public.genres (code, title) VALUES(?, ?);");
                 PreparedStatement preparedStatement2 = connection.prepareStatement("INSERT INTO public.genres_genres (genre_id, parent_id)  VALUES(?, ?);")) {
                preparedStatement.setString(1, parent);
                preparedStatement.setString(2, next.getKey());
                preparedStatement.execute();
                getChild(preparedStatement, preparedStatement2 , parent, next.getValue());
            }
        }
    }

    private void getChild(PreparedStatement preparedStatement, PreparedStatement preparedStatement2, String parent, JsonNode jsonNode) throws SQLException {
        final Iterator<JsonNode> elements = jsonNode.elements();
        while (elements.hasNext()) {
            final JsonNode next = elements.next();
            preparedStatement.setString(1, next.get("code").asText());
            preparedStatement.setString(2, next.get("name").asText());
            preparedStatement.execute();
            preparedStatement2.setString(1, next.get("code").asText());
            preparedStatement2.setString(2, parent);
            preparedStatement2.execute();
        }
    }
}
