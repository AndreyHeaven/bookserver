package ru.andreyheaven.bookserver.config;

import java.util.*;

public class IncludeExcludeProps {
    private List<String> include;
    private List<String> exclude;

    public List<String> getInclude() {
        return include;
    }

    public void setInclude(List<String> include) {
        this.include = include;
    }

    public List<String> getExclude() {
        return exclude;
    }

    public void setExclude(List<String> exclude) {
        this.exclude = exclude;
    }
}