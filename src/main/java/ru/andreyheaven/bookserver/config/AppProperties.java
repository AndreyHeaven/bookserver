package ru.andreyheaven.bookserver.config;

import org.springframework.boot.context.properties.*;
import java.util.*;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private IncludeExcludeProps genres;
    private IncludeExcludeProps files;
    private List<String> langs;
    private String dataDir;

    public IncludeExcludeProps getGenres() {
        return genres;
    }

    public void setGenres(IncludeExcludeProps genres) {
        this.genres = genres;
    }

    public IncludeExcludeProps getFiles() {
        return files;
    }

    public void setFiles(IncludeExcludeProps files) {
        this.files = files;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public List<String> getLangs() {
        return langs;
    }

    public void setLangs(List<String> langs) {
        this.langs = langs;
    }
}

